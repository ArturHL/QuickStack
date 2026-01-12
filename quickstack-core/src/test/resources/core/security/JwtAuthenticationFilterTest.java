package com.quickstack.core.security;

import com.quickstack.core.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests para JwtAuthenticationFilter.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    // ==================== REQUEST SIN TOKEN ====================

    @Test
    @DisplayName("Debe permitir request sin header Authorization")
    void shouldAllowRequestWithoutAuthHeader() throws ServletException, IOException {
        // Given - request sin header Authorization

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    // ==================== TOKEN VÁLIDO ====================

    @Test
    @DisplayName("Debe autenticar request con JWT válido")
    void shouldAuthenticateRequestWithValidJwt() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "admin@acme.com";
        Role role = Role.ADMIN;

        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractTenantId(token)).thenReturn(tenantId);
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(jwtService.extractRole(token)).thenReturn(role);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);

        // Verificar que se estableció Authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.getAuthorities())
                .hasSize(1)
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Verificar que se inyectó tenantId en request
        assertThat(request.getAttribute("tenantId")).isEqualTo(tenantId);
        assertThat(request.getAttribute("userId")).isEqualTo(userId);
        assertThat(request.getAttribute("userEmail")).isEqualTo(email);
    }

    // ==================== TOKEN INVÁLIDO ====================

    @Test
    @DisplayName("Debe rechazar request con JWT inválido")
    void shouldRejectRequestWithInvalidJwt() throws ServletException, IOException {
        // Given
        String invalidToken = "invalid.jwt.token";
        request.addHeader("Authorization", "Bearer " + invalidToken);

        when(jwtService.isTokenValid(invalidToken)).thenReturn(false);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService).isTokenValid(invalidToken);
        verify(jwtService, never()).extractUserId(any());
    }

    // ==================== HEADER MALFORMADO ====================

    @Test
    @DisplayName("Debe ignorar header Authorization sin 'Bearer '")
    void shouldIgnoreAuthHeaderWithoutBearerPrefix() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "InvalidPrefix token");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Debe ignorar header Authorization vacío")
    void shouldIgnoreEmptyAuthHeader() throws ServletException, IOException {
        // Given
        request.addHeader("Authorization", "Bearer ");

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    // ==================== USUARIO ROLE ====================

    @Test
    @DisplayName("Debe autenticar usuario con role USER")
    void shouldAuthenticateUserWithUserRole() throws ServletException, IOException {
        // Given
        String token = "valid.jwt.token";
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Role role = Role.USER;

        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.extractTenantId(token)).thenReturn(tenantId);
        when(jwtService.extractEmail(token)).thenReturn("user@acme.com");
        when(jwtService.extractRole(token)).thenReturn(role);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .hasSize(1)
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }
}

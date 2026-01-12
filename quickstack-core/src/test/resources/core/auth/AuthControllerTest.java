package com.quickstack.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.security.JwtAuthenticationFilter;
import com.quickstack.core.security.SecurityConfig;
import com.quickstack.core.security.ratelimit.RateLimitService;
import com.quickstack.core.user.Role;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para AuthController.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        // Configurar el filtro mock para que simplemente pase las peticiones sin autenticación
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        // Configurar RateLimitService para permitir todos los requests en tests
        Bucket mockBucket = org.mockito.Mockito.mock(Bucket.class);
        when(mockBucket.tryConsume(anyLong())).thenReturn(true);
        when(rateLimitService.resolveBucket(anyString(), anyInt(), anyInt(), anyLong()))
                .thenReturn(mockBucket);
    }

    // ==================== REGISTER TESTS ====================

    @Test
    @DisplayName("POST /api/auth/register - Debe registrar nuevo tenant")
    void shouldRegisterNewTenant() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .tenantName("Acme Corp")
                .tenantSlug("acme")
                .email("admin@acme.com")
                .password("password123")
                .userName("Admin User")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("jwt_token")
                .tokenType("Bearer")
                .userId(UUID.randomUUID())
                .email("admin@acme.com")
                .name("Admin User")
                .tenantId(UUID.randomUUID())
                .tenantName("Acme Corp")
                .role(Role.ADMIN)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", is("jwt_token")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.email", is("admin@acme.com")))
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Debe fallar con datos inválidos")
    void shouldFailRegisterWithInvalidData() throws Exception {
        // Given - request sin email
        RegisterRequest request = RegisterRequest.builder()
                .tenantName("Acme Corp")
                .tenantSlug("acme")
                .email("")  // Email vacío
                .password("password123")
                .userName("Admin User")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - Debe fallar si slug ya existe")
    void shouldFailRegisterIfSlugExists() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .tenantName("Acme Corp")
                .tenantSlug("acme")
                .email("admin@acme.com")
                .password("password123")
                .userName("Admin User")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new TenantAlreadyExistsException("acme"));

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("POST /api/auth/login - Debe autenticar usuario")
    void shouldLoginSuccessfully() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .email("admin@acme.com")
                .password("password123")
                .tenantSlug("acme")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("jwt_token")
                .tokenType("Bearer")
                .userId(UUID.randomUUID())
                .email("admin@acme.com")
                .name("Admin User")
                .tenantId(UUID.randomUUID())
                .tenantName("Acme Corp")
                .role(Role.ADMIN)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt_token")))
                .andExpect(jsonPath("$.email", is("admin@acme.com")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Debe fallar con credenciales inválidas")
    void shouldFailLoginWithInvalidCredentials() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .email("admin@acme.com")
                .password("wrongpassword")
                .tenantSlug("acme")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}

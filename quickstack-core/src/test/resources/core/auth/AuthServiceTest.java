package com.quickstack.core.auth;

import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.security.JwtService;
import com.quickstack.core.tenant.Tenant;
import com.quickstack.core.tenant.TenantRepository;
import com.quickstack.core.user.Role;
import com.quickstack.core.user.User;
import com.quickstack.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests para AuthService.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .tenantName("Acme Corp")
                .tenantSlug("acme")
                .email("admin@acme.com")
                .password("password123")
                .userName("Admin User")
                .build();

        loginRequest = LoginRequest.builder()
                .email("admin@acme.com")
                .password("password123")
                .tenantSlug("acme")
                .build();
    }

    // ==================== REGISTER TESTS ====================

    @Test
    @DisplayName("Register: Debe crear tenant y usuario admin")
    void shouldRegisterNewTenantWithAdmin() {
        // Given
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(), any(), anyString(), any())).thenReturn("jwt_token");

        // When
        AuthResponse response = authService.register(registerRequest);

        // Then
        assertThat(response.getAccessToken()).isEqualTo("jwt_token");
        assertThat(response.getEmail()).isEqualTo("admin@acme.com");
        assertThat(response.getTenantName()).isEqualTo("Acme Corp");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);

        // Verificar que se guardó el tenant
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getSlug()).isEqualTo("acme");

        // Verificar que se guardó el usuario con rol ADMIN
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed_password");
    }

    @Test
    @DisplayName("Register: Debe fallar si el slug ya existe")
    void shouldFailRegisterIfSlugExists() {
        // Given
        when(tenantRepository.existsBySlug("acme")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(TenantAlreadyExistsException.class)
                .hasMessageContaining("acme");

        verify(userRepository, never()).save(any());
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("Login: Debe autenticar usuario y devolver token")
    void shouldLoginSuccessfully() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Acme Corp")
                .slug("acme")
                .build();

        User user = User.builder()
                .id(userId)
                .email("admin@acme.com")
                .passwordHash("hashed_password")
                .name("Admin User")
                .tenantId(tenantId)
                .role(Role.ADMIN)
                .active(true)
                .build();

        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailAndTenantId("admin@acme.com", tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtService.generateToken(userId, tenantId, "admin@acme.com", Role.ADMIN)).thenReturn("jwt_token");

        // When
        AuthResponse response = authService.login(loginRequest);

        // Then
        assertThat(response.getAccessToken()).isEqualTo("jwt_token");
        assertThat(response.getEmail()).isEqualTo("admin@acme.com");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Login: Debe fallar si el tenant no existe")
    void shouldFailLoginIfTenantNotFound() {
        // Given
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login: Debe fallar si el usuario no existe")
    void shouldFailLoginIfUserNotFound() {
        // Given
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).slug("acme").build();
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailAndTenantId(anyString(), any())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login: Debe fallar si la contraseña es incorrecta")
    void shouldFailLoginIfPasswordIncorrect() {
        // Given
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).slug("acme").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@acme.com")
                .passwordHash("hashed_password")
                .tenantId(tenantId)
                .active(true)
                .build();

        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailAndTenantId("admin@acme.com", tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login: Debe fallar si el usuario está inactivo")
    void shouldFailLoginIfUserInactive() {
        // Given
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).slug("acme").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("admin@acme.com")
                .passwordHash("hashed_password")
                .tenantId(tenantId)
                .active(false)  // Usuario inactivo
                .build();

        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailAndTenantId("admin@acme.com", tenantId)).thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}

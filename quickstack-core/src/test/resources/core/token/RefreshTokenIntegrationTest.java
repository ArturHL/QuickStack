package com.quickstack.core.token;

import com.quickstack.core.audit.AuditLogRepository;
import com.quickstack.core.audit.SecurityEventType;
import com.quickstack.core.auth.AuthService;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.security.JwtService;
import com.quickstack.core.tenant.TenantRepository;
import com.quickstack.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Tests de integración end-to-end para Refresh Tokens.
 *
 * Cobertura:
 * - Flujo completo de autenticación con refresh tokens
 * - Generación de refresh token en login
 * - Rotación de tokens
 * - Revocación de tokens (logout)
 * - Logout de todas las sesiones
 * - Detección de reuso de tokens
 * - Expiración de tokens
 * - Auditoría de eventos de tokens
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@Transactional
class RefreshTokenIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID userId;
    private UUID tenantId;
    private String email;
    private String password;
    private String tenantSlug;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        email = "user@test.com";
        password = "password123";
        tenantSlug = "testcorp";
    }

    // ==================== LOGIN WITH REFRESH TOKEN TESTS ====================

    @Test
    @DisplayName("E2E: Login debe generar access token y refresh token")
    void loginShouldGenerateAccessAndRefreshTokens() {
        // Given - Registrar usuario
        registerUser();

        // When - Login
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .build();

        AuthResponse response = authService.login(loginRequest);

        // Then
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getExpiresIn()).isGreaterThan(0);

        // Verificar que el refresh token fue persistido
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("E2E: Refresh token debe persistirse con información correcta")
    void refreshTokenShouldBePersisteedWithCorrectInfo() {
        // Given
        registerUser();

        // When
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .build();

        AuthResponse response = authService.login(loginRequest);

        // Then
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);

        RefreshToken token = tokens.get(0);
        assertThat(token.getUserId()).isEqualTo(response.getUserId());
        assertThat(token.getTenantId()).isEqualTo(response.getTenantId());
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ==================== TOKEN REFRESH TESTS ====================

    @Test
    @DisplayName("E2E: Debe refrescar access token con refresh token válido")
    void shouldRefreshAccessTokenWithValidRefreshToken() {
        // Given - Login inicial
        registerUser();
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .build();

        AuthResponse loginResponse = authService.login(loginRequest);
        String oldAccessToken = loginResponse.getAccessToken();
        String refreshToken = loginResponse.getRefreshToken();

        // When - Refrescar token
        String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);

        // Then
        assertThat(newAccessToken).isNotNull();
        assertThat(newAccessToken).isNotEqualTo(oldAccessToken);
        assertThat(jwtService.isTokenValid(newAccessToken)).isTrue();
    }

    @Test
    @DisplayName("E2E: Refresh debe rotar refresh token (genera nuevo)")
    void refreshShouldRotateRefreshToken() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();
        String oldRefreshToken = loginResponse.getRefreshToken();

        long initialCount = refreshTokenRepository.count();

        // When - Rotar token
        RefreshToken newRefreshToken =
            refreshTokenService.rotateRefreshToken(oldRefreshToken);

        // Then
        assertThat(newRefreshToken).isNotNull();
        assertThat(newRefreshToken.getTokenHash()).isNotEqualTo(oldRefreshToken);

        // El token antiguo debe estar revocado
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<RefreshToken> allTokens = refreshTokenRepository.findAll();
            long revokedCount = allTokens.stream().filter(RefreshToken::isRevoked).count();
            assertThat(revokedCount).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("E2E: Múltiples refreshes consecutivos funcionan correctamente")
    void multipleConsecutiveRefreshesWorkCorrectly() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();
        String currentRefreshToken = loginResponse.getRefreshToken();

        // When - Múltiples refreshes
        for (int i = 0; i < 3; i++) {
            RefreshToken newToken = refreshTokenService.rotateRefreshToken(currentRefreshToken);
            currentRefreshToken = newToken.getTokenHash(); // Para siguiente iteración
        }

        // Then - Debe haber múltiples tokens revocados
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long revokedCount = refreshTokenRepository.findAll().stream()
                .filter(RefreshToken::isRevoked)
                .count();
            assertThat(revokedCount).isEqualTo(3);
        });
    }

    // ==================== TOKEN REVOCATION TESTS ====================

    @Test
    @DisplayName("E2E: Logout debe revocar refresh token")
    void logoutShouldRevokeRefreshToken() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();
        String refreshToken = loginResponse.getRefreshToken();

        // When - Logout
        refreshTokenService.revokeRefreshToken(refreshToken);

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<RefreshToken> tokens = refreshTokenRepository.findAll();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).isRevoked()).isTrue();
        });

        // Intentar usar el token revocado debe fallar
        assertThat(refreshTokenService.validateRefreshToken(refreshToken)).isFalse();
    }

    @Test
    @DisplayName("E2E: Logout-all debe revocar todos los tokens del usuario")
    void logoutAllShouldRevokeAllUserTokens() {
        // Given - Crear múltiples sesiones (múltiples logins)
        registerUser();

        AuthResponse login1 = login();
        AuthResponse login2 = login();
        AuthResponse login3 = login();

        long activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(
            login1.getUserId()
        ).size();
        assertThat(activeTokens).isEqualTo(3);

        // When - Logout-all
        int revokedCount = refreshTokenService.revokeAllUserTokens(login1.getUserId());

        // Then
        assertThat(revokedCount).isEqualTo(3);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            long stillActive = refreshTokenRepository.findByUserIdAndRevokedFalse(
                login1.getUserId()
            ).size();
            assertThat(stillActive).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("E2E: Token revocado no puede ser usado para refresh")
    void revokedTokenCannotBeUsedForRefresh() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();
        String refreshToken = loginResponse.getRefreshToken();

        // Revocar token
        refreshTokenService.revokeRefreshToken(refreshToken);

        // When & Then - Intentar refrescar con token revocado debe fallar
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(refreshToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    // ==================== TOKEN REUSE DETECTION TESTS ====================

    @Test
    @DisplayName("E2E: Debe detectar reuso de refresh token")
    void shouldDetectRefreshTokenReuse() {
        // Given - Login y primer refresh
        registerUser();
        AuthResponse loginResponse = login();
        String oldRefreshToken = loginResponse.getRefreshToken();

        // Hacer un refresh (esto revoca el token antiguo)
        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldRefreshToken);

        // When & Then - Intentar reusar el token antiguo revocado
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(oldRefreshToken))
            .isInstanceOf(TokenReuseException.class)
            .hasMessageContaining("Token reuse detected");
    }

    @Test
    @DisplayName("E2E: Detección de reuso debe revocar todos los tokens del usuario")
    void reuseDetectionShouldRevokeAllUserTokens() {
        // Given - Múltiples sesiones
        registerUser();
        AuthResponse login1 = login();
        AuthResponse login2 = login();

        String oldRefreshToken = login1.getRefreshToken();

        // Rotar el primer token
        refreshTokenService.rotateRefreshToken(oldRefreshToken);

        // When - Intentar reusar token revocado
        try {
            refreshTokenService.refreshAccessToken(oldRefreshToken);
        } catch (TokenReuseException e) {
            // Esperado
        }

        // Then - TODOS los tokens del usuario deben estar revocados
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(
                login1.getUserId()
            ).size();
            assertThat(activeTokens).isEqualTo(0);
        });
    }

    // ==================== TOKEN EXPIRATION TESTS ====================

    @Test
    @DisplayName("E2E: Token expirado debe ser rechazado")
    void expiredTokenShouldBeRejected() {
        // Given - Crear token ya expirado
        registerUser();
        AuthResponse loginResponse = login();

        RefreshToken token = refreshTokenRepository.findAll().get(0);
        token.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expirar
        refreshTokenRepository.save(token);

        // When & Then
        assertThat(refreshTokenService.validateRefreshToken(
            loginResponse.getRefreshToken()
        )).isFalse();
    }

    @Test
    @DisplayName("E2E: Cleanup debe eliminar tokens expirados")
    void cleanupShouldDeleteExpiredTokens() {
        // Given - Crear tokens expirados y activos
        registerUser();
        login(); // Token activo

        // Crear token expirado manualmente
        RefreshToken expiredToken = RefreshToken.builder()
            .tokenHash("expired-token-hash")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().minusDays(10))
            .revoked(false)
            .build();
        refreshTokenRepository.save(expiredToken);

        assertThat(refreshTokenRepository.count()).isEqualTo(2);

        // When - Limpiar tokens expirados
        refreshTokenService.cleanupExpiredTokens(LocalDateTime.now());

        // Then - Solo el token activo debe quedar
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll().get(0).getExpiresAt())
            .isAfter(LocalDateTime.now());
    }

    // ==================== AUDIT LOGGING TESTS ====================

    @Test
    @DisplayName("E2E: Refresh token debe registrar audit log")
    void refreshTokenShouldLogAuditEvent() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();

        auditLogRepository.deleteAll();

        // When - Refrescar token
        refreshTokenService.refreshAccessToken(loginResponse.getRefreshToken());

        // Then - Debe haber audit log de TOKEN_REFRESH
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long refreshEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.TOKEN_REFRESH)
                .count();
            assertThat(refreshEvents).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("E2E: Logout debe registrar audit log")
    void logoutShouldLogAuditEvent() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();

        auditLogRepository.deleteAll();

        // When - Logout
        refreshTokenService.revokeRefreshToken(loginResponse.getRefreshToken());

        // Then - Debe haber audit log de LOGOUT
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long logoutEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.LOGOUT)
                .count();
            assertThat(logoutEvents).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("E2E: Detección de reuso debe registrar evento de seguridad")
    void reuseDetectionShouldLogSecurityEvent() {
        // Given
        registerUser();
        AuthResponse loginResponse = login();
        String oldRefreshToken = loginResponse.getRefreshToken();

        // Rotar token
        refreshTokenService.rotateRefreshToken(oldRefreshToken);

        auditLogRepository.deleteAll();

        // When - Intentar reusar token
        try {
            refreshTokenService.refreshAccessToken(oldRefreshToken);
        } catch (TokenReuseException e) {
            // Esperado
        }

        // Then - Debe haber audit log de actividad sospechosa
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long suspiciousEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.SUSPICIOUS_ACTIVITY)
                .count();
            assertThat(suspiciousEvents).isGreaterThan(0);
        });
    }

    // ==================== DEVICE INFO TESTS ====================

    @Test
    @DisplayName("E2E: Debe almacenar información del dispositivo en login")
    void shouldStoreDeviceInfoOnLogin() {
        // Given
        registerUser();

        // When - Login con device info
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .deviceInfo("Chrome 120.0 / Windows 10")
            .build();

        authService.login(loginRequest);

        // Then
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getDeviceInfo()).isEqualTo("Chrome 120.0 / Windows 10");
    }

    @Test
    @DisplayName("E2E: Puede listar sesiones activas del usuario")
    void canListUserActiveSessions() {
        // Given - Múltiples sesiones desde diferentes dispositivos
        registerUser();

        LoginRequest chrome = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .deviceInfo("Chrome / Windows")
            .build();

        LoginRequest firefox = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .deviceInfo("Firefox / Linux")
            .build();

        LoginRequest safari = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .deviceInfo("Safari / macOS")
            .build();

        AuthResponse resp1 = authService.login(chrome);
        authService.login(firefox);
        authService.login(safari);

        // When - Listar sesiones activas
        List<RefreshToken> activeSessions =
            refreshTokenRepository.findByUserIdAndRevokedFalse(resp1.getUserId());

        // Then
        assertThat(activeSessions).hasSize(3);
        assertThat(activeSessions).extracting("deviceInfo")
            .containsExactlyInAnyOrder(
                "Chrome / Windows",
                "Firefox / Linux",
                "Safari / macOS"
            );
    }

    // ==================== HELPER METHODS ====================

    private void registerUser() {
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug(tenantSlug)
            .email(email)
            .password(password)
            .userName("Test User")
            .build();

        AuthResponse response = authService.register(registerRequest);
        userId = response.getUserId();
        tenantId = response.getTenantId();
    }

    private AuthResponse login() {
        LoginRequest loginRequest = LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .build();

        return authService.login(loginRequest);
    }
}

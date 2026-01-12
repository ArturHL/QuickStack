package com.quickstack.core.token;

import com.quickstack.core.audit.AuditService;
import com.quickstack.core.audit.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para RefreshTokenService.
 *
 * Cobertura:
 * - Generación de refresh tokens
 * - Validación de refresh tokens
 * - Rotación de tokens (refresh genera nuevo token)
 * - Revocación de tokens (logout)
 * - Revocación de todos los tokens de un usuario (logout-all)
 * - Detección de reuso de tokens (seguridad)
 * - Expiración de tokens
 * - Auditoría de eventos de tokens
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private UUID userId;
    private UUID tenantId;
    private String deviceInfo;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        deviceInfo = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    }

    // ==================== TOKEN GENERATION TESTS ====================

    @Test
    @DisplayName("Debe generar refresh token con todos los campos correctos")
    void shouldGenerateRefreshTokenWithAllFields() {
        // Given
        String tokenValue = "generated-refresh-token-value";
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            deviceInfo
        );

        // Then
        assertThat(token).isNotNull();
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getTenantId()).isEqualTo(tenantId);
        assertThat(token.getDeviceInfo()).isEqualTo(deviceInfo);
        assertThat(token.getTokenHash()).isNotNull();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(token.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe generar token con 30 días de expiración por defecto")
    void shouldGenerateTokenWith30DaysExpiration() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            deviceInfo
        );

        // Then
        LocalDateTime expectedExpiry = LocalDateTime.now().plusDays(30);
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(token.getExpiresAt()).isBefore(expectedExpiry.plusHours(1));
    }

    @Test
    @DisplayName("Debe hashear el token antes de persistir")
    void shouldHashTokenBeforePersisting() {
        // Given
        String hashedValue = "hashed-token-value";
        when(passwordEncoder.encode(anyString())).thenReturn(hashedValue);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            deviceInfo
        );

        // Then
        assertThat(token.getTokenHash()).isEqualTo(hashedValue);
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    @DisplayName("Debe retornar valor de token en texto plano una sola vez")
    void shouldReturnPlainTextTokenOnlyOnce() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            deviceInfo
        );

        // Then - El servicio debe retornar el token en texto plano
        // pero luego solo se almacena el hash
        assertThat(token.getTokenHash()).isNotNull();
    }

    // ==================== TOKEN VALIDATION TESTS ====================

    @Test
    @DisplayName("Debe validar refresh token válido correctamente")
    void shouldValidateValidRefreshToken() {
        // Given
        String plainToken = "valid-refresh-token";
        String hashedToken = "hashed-value";

        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(refreshToken));
        when(passwordEncoder.matches(plainToken, hashedToken)).thenReturn(true);

        // When
        boolean isValid = refreshTokenService.validateRefreshToken(plainToken);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar refresh token inexistente")
    void shouldRejectNonExistentRefreshToken() {
        // Given
        String plainToken = "non-existent-token";
        when(refreshTokenRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.empty());

        // When
        boolean isValid = refreshTokenService.validateRefreshToken(plainToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar refresh token expirado")
    void shouldRejectExpiredRefreshToken() {
        // Given
        String plainToken = "expired-token";
        String hashedToken = "hashed-expired";

        RefreshToken expiredToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().minusDays(1)) // Expirado
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(expiredToken));
        when(passwordEncoder.matches(plainToken, hashedToken)).thenReturn(true);

        // When
        boolean isValid = refreshTokenService.validateRefreshToken(plainToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar refresh token revocado")
    void shouldRejectRevokedRefreshToken() {
        // Given
        String plainToken = "revoked-token";
        String hashedToken = "hashed-revoked";

        RefreshToken revokedToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true) // Revocado
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(revokedToken));
        when(passwordEncoder.matches(plainToken, hashedToken)).thenReturn(true);

        // When
        boolean isValid = refreshTokenService.validateRefreshToken(plainToken);

        // Then
        assertThat(isValid).isFalse();
    }

    // ==================== TOKEN REFRESH TESTS ====================

    @Test
    @DisplayName("Debe generar nuevo access token desde refresh token válido")
    void shouldGenerateNewAccessTokenFromValidRefreshToken() {
        // Given
        String plainRefreshToken = "valid-refresh-token";
        String hashedToken = "hashed-valid";

        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(refreshToken));
        when(passwordEncoder.matches(plainRefreshToken, hashedToken)).thenReturn(true);

        // When
        String newAccessToken = refreshTokenService.refreshAccessToken(plainRefreshToken);

        // Then
        assertThat(newAccessToken).isNotNull();
        assertThat(newAccessToken).isNotEmpty();
    }

    @Test
    @DisplayName("Debe rotar refresh token al hacer refresh (genera nuevo)")
    void shouldRotateRefreshTokenOnRefresh() {
        // Given
        String oldRefreshToken = "old-refresh-token";
        String hashedOldToken = "hashed-old";

        RefreshToken oldToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedOldToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedOldToken))
            .thenReturn(Optional.of(oldToken));
        when(passwordEncoder.matches(oldRefreshToken, hashedOldToken)).thenReturn(true);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldRefreshToken);

        // Then
        assertThat(newRefreshToken).isNotNull();
        assertThat(newRefreshToken.getId()).isNotEqualTo(oldToken.getId());
        assertThat(newRefreshToken.getTokenHash()).isNotEqualTo(oldToken.getTokenHash());

        // Verificar que el token antiguo fue revocado
        verify(refreshTokenRepository).save(argThat(token ->
            token.getId().equals(oldToken.getId()) && token.isRevoked()
        ));
    }

    @Test
    @DisplayName("Debe registrar audit log al hacer token refresh")
    void shouldLogAuditEventOnTokenRefresh() {
        // Given
        String refreshToken = "valid-refresh-token";
        String hashedToken = "hashed-valid";

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(refreshToken, hashedToken)).thenReturn(true);

        // When
        refreshTokenService.refreshAccessToken(refreshToken);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.TOKEN_REFRESH),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            any(Map.class)
        );
    }

    // ==================== TOKEN REVOCATION TESTS ====================

    @Test
    @DisplayName("Debe revocar refresh token (logout)")
    void shouldRevokeRefreshToken() {
        // Given
        String refreshToken = "token-to-revoke";
        String hashedToken = "hashed-to-revoke";

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(refreshToken, hashedToken)).thenReturn(true);

        // When
        refreshTokenService.revokeRefreshToken(refreshToken);

        // Then
        verify(refreshTokenRepository).save(argThat(t ->
            t.getId().equals(token.getId()) && t.isRevoked()
        ));
    }

    @Test
    @DisplayName("Debe registrar audit log al revocar token (logout)")
    void shouldLogAuditEventOnTokenRevocation() {
        // Given
        String refreshToken = "token-to-revoke";
        String hashedToken = "hashed-to-revoke";

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(refreshToken, hashedToken)).thenReturn(true);

        // When
        refreshTokenService.revokeRefreshToken(refreshToken);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.LOGOUT),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            any(Map.class)
        );
    }

    @Test
    @DisplayName("Debe revocar todos los tokens de un usuario (logout-all)")
    void shouldRevokeAllUserTokens() {
        // Given
        RefreshToken token1 = RefreshToken.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .tenantId(tenantId)
            .revoked(false)
            .build();

        RefreshToken token2 = RefreshToken.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .tenantId(tenantId)
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId))
            .thenReturn(java.util.List.of(token1, token2));

        // When
        int revokedCount = refreshTokenService.revokeAllUserTokens(userId);

        // Then
        assertThat(revokedCount).isEqualTo(2);
        verify(refreshTokenRepository).saveAll(argThat(tokens -> {
            java.util.List<RefreshToken> tokenList =
                (java.util.List<RefreshToken>) tokens;
            return tokenList.size() == 2 &&
                   tokenList.stream().allMatch(RefreshToken::isRevoked);
        }));
    }

    @Test
    @DisplayName("Debe retornar 0 si el usuario no tiene tokens activos al hacer logout-all")
    void shouldReturnZeroIfNoActiveTokensOnLogoutAll() {
        // Given
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId))
            .thenReturn(java.util.List.of());

        // When
        int revokedCount = refreshTokenService.revokeAllUserTokens(userId);

        // Then
        assertThat(revokedCount).isEqualTo(0);
        verify(refreshTokenRepository, never()).saveAll(any());
    }

    // ==================== TOKEN REUSE DETECTION TESTS ====================

    @Test
    @DisplayName("Debe detectar reuso de refresh token revocado (seguridad)")
    void shouldDetectRefreshTokenReuse() {
        // Given - Token ya revocado
        String revokedToken = "reused-token";
        String hashedToken = "hashed-reused";

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true) // Ya revocado
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(revokedToken, hashedToken)).thenReturn(true);

        // When
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(revokedToken))
            .isInstanceOf(TokenReuseException.class)
            .hasMessageContaining("Token reuse detected");

        // Then - Debe revocar TODOS los tokens del usuario por seguridad
        verify(refreshTokenRepository).findByUserIdAndRevokedFalse(userId);
    }

    @Test
    @DisplayName("Debe revocar todos los tokens del usuario al detectar reuso")
    void shouldRevokeAllTokensOnReuseDetection() {
        // Given - Token revocado siendo reusado
        String revokedToken = "reused-token";
        String hashedToken = "hashed-reused";

        RefreshToken revokedRefreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true)
            .build();

        RefreshToken activeToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .tenantId(tenantId)
            .revoked(false)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(revokedRefreshToken));
        when(passwordEncoder.matches(revokedToken, hashedToken)).thenReturn(true);
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId))
            .thenReturn(java.util.List.of(activeToken));

        // When
        try {
            refreshTokenService.refreshAccessToken(revokedToken);
        } catch (TokenReuseException e) {
            // Esperado
        }

        // Then
        verify(refreshTokenRepository).saveAll(any());
    }

    @Test
    @DisplayName("Debe registrar audit log de seguridad al detectar reuso")
    void shouldLogSecurityAuditOnReuseDetection() {
        // Given
        String revokedToken = "reused-token";
        String hashedToken = "hashed-reused";

        RefreshToken token = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash(hashedToken)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true)
            .build();

        when(refreshTokenRepository.findByTokenHash(hashedToken))
            .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(revokedToken, hashedToken)).thenReturn(true);
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(userId))
            .thenReturn(java.util.List.of());

        // When
        try {
            refreshTokenService.refreshAccessToken(revokedToken);
        } catch (TokenReuseException e) {
            // Esperado
        }

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.SUSPICIOUS_ACTIVITY),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            argThat(details ->
                details.containsKey("reason") &&
                details.get("reason").toString().contains("Token reuse")
            )
        );
    }

    // ==================== DEVICE INFO TESTS ====================

    @Test
    @DisplayName("Debe almacenar información del dispositivo")
    void shouldStoreDeviceInformation() {
        // Given
        String deviceInfo = "Chrome 120.0 / Windows 10";
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            deviceInfo
        );

        // Then
        assertThat(token.getDeviceInfo()).isEqualTo(deviceInfo);
    }

    @Test
    @DisplayName("Debe permitir device info nulo")
    void shouldAllowNullDeviceInfo() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token = refreshTokenService.generateRefreshToken(
            userId,
            tenantId,
            null
        );

        // Then
        assertThat(token.getDeviceInfo()).isNull();
    }

    // ==================== CLEANUP TESTS ====================

    @Test
    @DisplayName("Debe limpiar tokens expirados automáticamente")
    void shouldCleanupExpiredTokensAutomatically() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

        // When
        refreshTokenService.cleanupExpiredTokens(cutoffDate);

        // Then
        verify(refreshTokenRepository).deleteByExpiresAtBefore(cutoffDate);
    }

    @Test
    @DisplayName("Debe limpiar tokens revocados antiguos")
    void shouldCleanupOldRevokedTokens() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        // When
        refreshTokenService.cleanupRevokedTokens(cutoffDate);

        // Then
        verify(refreshTokenRepository).deleteByRevokedTrueAndCreatedAtBefore(cutoffDate);
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("Debe lanzar excepción al intentar refrescar con token nulo")
    void shouldThrowExceptionOnNullTokenRefresh() {
        // When & Then
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("token cannot be null");
    }

    @Test
    @DisplayName("Debe lanzar excepción al intentar refrescar con token vacío")
    void shouldThrowExceptionOnEmptyTokenRefresh() {
        // When & Then
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("token cannot be empty");
    }

    @Test
    @DisplayName("Debe manejar error de base de datos gracefully")
    void shouldHandleDatabaseErrorGracefully() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() ->
            refreshTokenService.generateRefreshToken(userId, tenantId, deviceInfo)
        ).isInstanceOf(RuntimeException.class);
    }
}

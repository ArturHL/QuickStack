package com.quickstack.core.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de persistencia para RefreshTokenRepository.
 *
 * Cobertura:
 * - Query methods personalizados
 * - Búsqueda por token hash
 * - Búsqueda por usuario
 * - Filtrado por estado (revocado/activo)
 * - Limpieza de tokens expirados y revocados
 * - Persistencia de device info
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private UUID userId1;
    private UUID userId2;
    private UUID tenantId1;
    private UUID tenantId2;

    @BeforeEach
    void setUp() {
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        tenantId1 = UUID.randomUUID();
        tenantId2 = UUID.randomUUID();

        refreshTokenRepository.deleteAll();
    }

    // ==================== BASIC PERSISTENCE TESTS ====================

    @Test
    @DisplayName("Debe guardar y recuperar refresh token con todos los campos")
    void shouldSaveAndRetrieveRefreshToken() {
        // Given
        RefreshToken token = RefreshToken.builder()
            .tokenHash("hashed-token-value")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .deviceInfo("Chrome 120.0 / Windows 10")
            .build();

        // When
        RefreshToken saved = refreshTokenRepository.save(token);
        RefreshToken retrieved = refreshTokenRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getTokenHash()).isEqualTo("hashed-token-value");
        assertThat(retrieved.getUserId()).isEqualTo(userId1);
        assertThat(retrieved.getTenantId()).isEqualTo(tenantId1);
        assertThat(retrieved.getExpiresAt()).isNotNull();
        assertThat(retrieved.isRevoked()).isFalse();
        assertThat(retrieved.getDeviceInfo()).isEqualTo("Chrome 120.0 / Windows 10");
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe permitir device info nulo")
    void shouldAllowNullDeviceInfo() {
        // Given
        RefreshToken token = RefreshToken.builder()
            .tokenHash("token-without-device-info")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .deviceInfo(null)
            .build();

        // When
        RefreshToken saved = refreshTokenRepository.save(token);
        RefreshToken retrieved = refreshTokenRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDeviceInfo()).isNull();
    }

    // ==================== FIND BY TOKEN HASH TESTS ====================

    @Test
    @DisplayName("Debe encontrar token por hash")
    void shouldFindTokenByHash() {
        // Given
        String tokenHash = "unique-token-hash";
        RefreshToken token = createRefreshToken(tokenHash, userId1, tenantId1, false);

        // When
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(tokenHash);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    @DisplayName("Debe retornar empty si el token hash no existe")
    void shouldReturnEmptyIfTokenHashNotFound() {
        // When
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("non-existent");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Token hash debe ser único")
    void tokenHashShouldBeUnique() {
        // Given
        String duplicateHash = "duplicate-hash";
        createRefreshToken(duplicateHash, userId1, tenantId1, false);

        // When & Then - Intentar insertar otro con el mismo hash debe fallar
        RefreshToken duplicate = RefreshToken.builder()
            .tokenHash(duplicateHash)
            .userId(userId2)
            .tenantId(tenantId2)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .build();

        try {
            refreshTokenRepository.save(duplicate);
            refreshTokenRepository.flush();
            // Si no hay constraint de unicidad, el test debe alertar
            assertThat(false).as("Should enforce unique token_hash constraint").isTrue();
        } catch (Exception e) {
            // Esperado - constraint violation
            assertThat(e).isNotNull();
        }
    }

    // ==================== FIND BY USER ID TESTS ====================

    @Test
    @DisplayName("Debe encontrar todos los tokens de un usuario")
    void shouldFindAllTokensByUserId() {
        // Given
        createRefreshToken("token1", userId1, tenantId1, false);
        createRefreshToken("token2", userId1, tenantId1, false);
        createRefreshToken("token3", userId1, tenantId1, true); // Revocado
        createRefreshToken("token4", userId2, tenantId2, false); // Otro usuario

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId1);

        // Then
        assertThat(tokens).hasSize(3); // Todos los tokens del usuario1
        assertThat(tokens).allMatch(t -> t.getUserId().equals(userId1));
    }

    @Test
    @DisplayName("Debe encontrar solo tokens activos de un usuario")
    void shouldFindOnlyActiveTokensByUserId() {
        // Given
        createRefreshToken("active1", userId1, tenantId1, false);
        createRefreshToken("active2", userId1, tenantId1, false);
        createRefreshToken("revoked", userId1, tenantId1, true);

        // When
        List<RefreshToken> activeTokens =
            refreshTokenRepository.findByUserIdAndRevokedFalse(userId1);

        // Then
        assertThat(activeTokens).hasSize(2);
        assertThat(activeTokens).allMatch(t -> !t.isRevoked());
    }

    @Test
    @DisplayName("Debe retornar lista vacía si el usuario no tiene tokens")
    void shouldReturnEmptyListIfUserHasNoTokens() {
        // When
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(UUID.randomUUID());

        // Then
        assertThat(tokens).isEmpty();
    }

    // ==================== FIND BY TENANT ID TESTS ====================

    @Test
    @DisplayName("Debe encontrar todos los tokens de un tenant")
    void shouldFindAllTokensByTenantId() {
        // Given
        createRefreshToken("tenant1-token1", userId1, tenantId1, false);
        createRefreshToken("tenant1-token2", userId2, tenantId1, false);
        createRefreshToken("tenant2-token1", userId1, tenantId2, false);

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findByTenantId(tenantId1);

        // Then
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(t -> t.getTenantId().equals(tenantId1));
    }

    // ==================== EXPIRATION TESTS ====================

    @Test
    @DisplayName("Debe encontrar tokens expirados")
    void shouldFindExpiredTokens() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        RefreshToken expired1 = RefreshToken.builder()
            .tokenHash("expired1")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(now.minusDays(1))
            .revoked(false)
            .build();

        RefreshToken expired2 = RefreshToken.builder()
            .tokenHash("expired2")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(now.minusDays(10))
            .revoked(false)
            .build();

        RefreshToken active = RefreshToken.builder()
            .tokenHash("active")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(now.plusDays(10))
            .revoked(false)
            .build();

        refreshTokenRepository.saveAll(List.of(expired1, expired2, active));

        // When
        List<RefreshToken> expiredTokens =
            refreshTokenRepository.findByExpiresAtBefore(now);

        // Then
        assertThat(expiredTokens).hasSize(2);
        assertThat(expiredTokens).allMatch(t -> t.getExpiresAt().isBefore(now));
    }

    @Test
    @DisplayName("Debe eliminar tokens expirados")
    void shouldDeleteExpiredTokens() {
        // Given
        LocalDateTime cutoff = LocalDateTime.now();

        RefreshToken expired = RefreshToken.builder()
            .tokenHash("expired")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(cutoff.minusDays(1))
            .revoked(false)
            .build();

        RefreshToken active = RefreshToken.builder()
            .tokenHash("active")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(cutoff.plusDays(10))
            .revoked(false)
            .build();

        refreshTokenRepository.saveAll(List.of(expired, active));
        refreshTokenRepository.flush();

        long initialCount = refreshTokenRepository.count();
        assertThat(initialCount).isEqualTo(2);

        // When
        int deleted = refreshTokenRepository.deleteByExpiresAtBefore(cutoff);

        // Then
        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("active")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
    }

    // ==================== REVOCATION TESTS ====================

    @Test
    @DisplayName("Debe actualizar estado de revocación")
    void shouldUpdateRevocationStatus() {
        // Given
        RefreshToken token = createRefreshToken("revoke-test", userId1, tenantId1, false);
        assertThat(token.isRevoked()).isFalse();

        // When - Revocar token
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        refreshTokenRepository.flush();

        // Then
        RefreshToken updated = refreshTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updated.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Debe encontrar solo tokens revocados")
    void shouldFindOnlyRevokedTokens() {
        // Given
        createRefreshToken("revoked1", userId1, tenantId1, true);
        createRefreshToken("revoked2", userId1, tenantId1, true);
        createRefreshToken("active", userId1, tenantId1, false);

        // When
        List<RefreshToken> revokedTokens =
            refreshTokenRepository.findByUserIdAndRevokedTrue(userId1);

        // Then
        assertThat(revokedTokens).hasSize(2);
        assertThat(revokedTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("Debe eliminar tokens revocados antiguos")
    void shouldDeleteOldRevokedTokens() {
        // Given
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        RefreshToken oldRevoked = RefreshToken.builder()
            .tokenHash("old-revoked")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true)
            .createdAt(cutoff.minusDays(10))
            .build();

        RefreshToken recentRevoked = RefreshToken.builder()
            .tokenHash("recent-revoked")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(true)
            .createdAt(LocalDateTime.now())
            .build();

        RefreshToken active = createRefreshToken("active", userId1, tenantId1, false);

        refreshTokenRepository.saveAll(List.of(oldRevoked, recentRevoked, active));
        refreshTokenRepository.flush();

        // When
        int deleted = refreshTokenRepository.deleteByRevokedTrueAndCreatedAtBefore(cutoff);

        // Then
        assertThat(deleted).isEqualTo(1); // Solo oldRevoked
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    // ==================== DEVICE INFO TESTS ====================

    @Test
    @DisplayName("Debe persistir información del dispositivo correctamente")
    void shouldPersistDeviceInfoCorrectly() {
        // Given
        String deviceInfo = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";
        RefreshToken token = RefreshToken.builder()
            .tokenHash("device-test")
            .userId(userId1)
            .tenantId(tenantId1)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .deviceInfo(deviceInfo)
            .build();

        // When
        RefreshToken saved = refreshTokenRepository.save(token);
        refreshTokenRepository.flush();
        RefreshToken retrieved = refreshTokenRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(retrieved.getDeviceInfo()).isEqualTo(deviceInfo);
    }

    @Test
    @DisplayName("Debe soportar device info largo")
    void shouldSupportLongDeviceInfo() {
        // Given
        String longDeviceInfo = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";

        RefreshToken token = createRefreshToken("long-device", userId1, tenantId1, false);
        token.setDeviceInfo(longDeviceInfo);

        // When
        RefreshToken saved = refreshTokenRepository.save(token);
        refreshTokenRepository.flush();

        // Then
        RefreshToken retrieved = refreshTokenRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getDeviceInfo()).hasSize(longDeviceInfo.length());
    }

    // ==================== COUNT AND STATISTICS TESTS ====================

    @Test
    @DisplayName("Debe contar tokens activos por usuario")
    void shouldCountActiveTokensByUser() {
        // Given
        createRefreshToken("active1", userId1, tenantId1, false);
        createRefreshToken("active2", userId1, tenantId1, false);
        createRefreshToken("revoked", userId1, tenantId1, true);

        // When
        long count = refreshTokenRepository.countByUserIdAndRevokedFalse(userId1);

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe contar todos los tokens de un usuario")
    void shouldCountAllTokensByUser() {
        // Given
        createRefreshToken("token1", userId1, tenantId1, false);
        createRefreshToken("token2", userId1, tenantId1, true);
        createRefreshToken("token3", userId1, tenantId1, false);

        // When
        long count = refreshTokenRepository.countByUserId(userId1);

        // Then
        assertThat(count).isEqualTo(3);
    }

    // ==================== BATCH OPERATIONS TESTS ====================

    @Test
    @DisplayName("Debe actualizar múltiples tokens en batch")
    void shouldUpdateMultipleTokensInBatch() {
        // Given
        RefreshToken token1 = createRefreshToken("batch1", userId1, tenantId1, false);
        RefreshToken token2 = createRefreshToken("batch2", userId1, tenantId1, false);
        RefreshToken token3 = createRefreshToken("batch3", userId1, tenantId1, false);

        // When - Revocar todos
        token1.setRevoked(true);
        token2.setRevoked(true);
        token3.setRevoked(true);

        refreshTokenRepository.saveAll(List.of(token1, token2, token3));
        refreshTokenRepository.flush();

        // Then
        List<RefreshToken> allTokens = refreshTokenRepository.findByUserId(userId1);
        assertThat(allTokens).hasSize(3);
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);
    }

    // ==================== HELPER METHODS ====================

    private RefreshToken createRefreshToken(
        String tokenHash,
        UUID userId,
        UUID tenantId,
        boolean revoked
    ) {
        RefreshToken token = RefreshToken.builder()
            .tokenHash(tokenHash)
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(revoked)
            .deviceInfo("Test Device")
            .build();
        return refreshTokenRepository.save(token);
    }
}

package com.quickstack.core.security;

import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para JwtKeyProvider.
 *
 * Cobertura:
 * - Gestión de múltiples claves activas
 * - Key ID (kid) en tokens JWT
 * - Rotación de claves con período de gracia
 * - Recuperación de claves por ID
 * - Validación de claves expiradas
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@ExtendWith(MockitoExtension.class)
class JwtKeyProviderTest {

    @Mock
    private SecretsService secretsService;

    private JwtKeyProvider keyProvider;

    private String currentSecretKey;
    private String previousSecretKey;

    @BeforeEach
    void setUp() {
        currentSecretKey = "current-jwt-secret-key-minimum-32-characters-long-required";
        previousSecretKey = "previous-jwt-secret-key-minimum-32-characters-long-needed";
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    @DisplayName("Debe inicializar con una clave actual desde SecretsService")
    void shouldInitializeWithCurrentKey() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);

        // When
        keyProvider = new JwtKeyProvider(secretsService);

        // Then
        SecretKey currentKey = keyProvider.getCurrentKey();
        assertThat(currentKey).isNotNull();
    }

    @Test
    @DisplayName("Debe generar un Key ID único para la clave actual")
    void shouldGenerateUniqueKeyId() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);

        // When
        keyProvider = new JwtKeyProvider(secretsService);
        String kid = keyProvider.getCurrentKeyId();

        // Then
        assertThat(kid).isNotNull();
        assertThat(kid).isNotEmpty();
        assertThat(kid).hasSize(8); // Por ejemplo, primeros 8 chars de un hash
    }

    // ==================== CURRENT KEY TESTS ====================

    @Test
    @DisplayName("Debe retornar la clave actual correctamente")
    void shouldReturnCurrentKey() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When
        SecretKey key = keyProvider.getCurrentKey();

        // Then
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    @DisplayName("Debe retornar el mismo Key ID para múltiples llamadas")
    void shouldReturnSameKeyIdForMultipleCalls() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When
        String kid1 = keyProvider.getCurrentKeyId();
        String kid2 = keyProvider.getCurrentKeyId();

        // Then
        assertThat(kid1).isEqualTo(kid2);
    }

    // ==================== KEY RETRIEVAL BY ID TESTS ====================

    @Test
    @DisplayName("Debe recuperar clave actual por Key ID")
    void shouldRetrieveCurrentKeyById() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String currentKid = keyProvider.getCurrentKeyId();

        // When
        Optional<SecretKey> key = keyProvider.getKeyById(currentKid);

        // Then
        assertThat(key).isPresent();
        assertThat(key.get()).isEqualTo(keyProvider.getCurrentKey());
    }

    @Test
    @DisplayName("Debe retornar Optional.empty() para Key ID desconocido")
    void shouldReturnEmptyForUnknownKeyId() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When
        Optional<SecretKey> key = keyProvider.getKeyById("unknown-key-id");

        // Then
        assertThat(key).isEmpty();
    }

    // ==================== KEY ROTATION TESTS ====================

    @Test
    @DisplayName("Debe agregar una nueva clave durante rotación")
    void shouldAddNewKeyDuringRotation() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String oldKid = keyProvider.getCurrentKeyId();

        // When - Rotar clave
        String newSecretKey = "new-rotated-jwt-secret-key-minimum-32-chars-required-here";
        keyProvider.rotateKey(newSecretKey);

        // Then
        String newKid = keyProvider.getCurrentKeyId();
        assertThat(newKid).isNotEqualTo(oldKid);

        // La clave antigua debe seguir siendo válida (período de gracia)
        Optional<SecretKey> oldKey = keyProvider.getKeyById(oldKid);
        assertThat(oldKey).isPresent();
    }

    @Test
    @DisplayName("Debe mantener clave anterior válida durante período de gracia")
    void shouldKeepPreviousKeyValidDuringGracePeriod() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String oldKid = keyProvider.getCurrentKeyId();
        SecretKey oldKey = keyProvider.getCurrentKey();

        // When - Rotar clave
        String newSecretKey = "new-rotated-secret-key-minimum-32-characters-long-needed";
        keyProvider.rotateKey(newSecretKey);

        // Then - Ambas claves deben ser válidas
        assertThat(keyProvider.getKeyById(oldKid)).isPresent();
        assertThat(keyProvider.getKeyById(keyProvider.getCurrentKeyId())).isPresent();

        // Verificar que son diferentes
        assertThat(keyProvider.getCurrentKey()).isNotEqualTo(oldKey);
    }

    @Test
    @DisplayName("Debe establecer período de gracia de 24 horas para clave rotada")
    void shouldSetGracePeriodFor24Hours() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String oldKid = keyProvider.getCurrentKeyId();

        Instant beforeRotation = Instant.now();

        // When
        String newSecretKey = "new-secret-key-after-rotation-32-chars-minimum-needed";
        keyProvider.rotateKey(newSecretKey);

        // Then - Verificar que la clave tiene un período de gracia configurado
        Optional<Instant> expiryTime = keyProvider.getKeyExpiryTime(oldKid);
        assertThat(expiryTime).isPresent();

        // El tiempo de expiración debe ser aproximadamente 24 horas después
        Instant expectedExpiry = beforeRotation.plusSeconds(24 * 60 * 60);
        assertThat(expiryTime.get()).isAfterOrEqualTo(expectedExpiry.minusSeconds(5));
        assertThat(expiryTime.get()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(5));
    }

    // ==================== KEY EXPIRY TESTS ====================

    @Test
    @DisplayName("Debe remover claves expiradas automáticamente")
    void shouldRemoveExpiredKeysAutomatically() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String oldKid = keyProvider.getCurrentKeyId();

        // Rotar y establecer expiración inmediata (para prueba)
        String newSecretKey = "new-secret-key-rotation-32-chars-minimum-required-here";
        keyProvider.rotateKey(newSecretKey);
        keyProvider.setKeyExpiryTime(oldKid, Instant.now().minusSeconds(1)); // Expirada

        // When - Limpiar claves expiradas
        keyProvider.cleanupExpiredKeys();

        // Then - La clave expirada no debe estar disponible
        Optional<SecretKey> expiredKey = keyProvider.getKeyById(oldKid);
        assertThat(expiredKey).isEmpty();
    }

    @Test
    @DisplayName("Debe mantener claves no expiradas durante cleanup")
    void shouldKeepNonExpiredKeysDuringCleanup() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String currentKid = keyProvider.getCurrentKeyId();

        // Rotar clave
        String newSecretKey = "another-new-secret-key-32-characters-minimum-needed";
        keyProvider.rotateKey(newSecretKey);
        String newKid = keyProvider.getCurrentKeyId();

        // When - Limpiar (la clave actual no debe expirar)
        keyProvider.cleanupExpiredKeys();

        // Then
        assertThat(keyProvider.getKeyById(newKid)).isPresent();
        assertThat(keyProvider.getKeyById(currentKid)).isPresent(); // Aún en gracia
    }

    @Test
    @DisplayName("No debe retornar clave expirada incluso si está en el mapa")
    void shouldNotReturnExpiredKeyEvenIfInMap() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String oldKid = keyProvider.getCurrentKeyId();

        // Rotar y expirar inmediatamente
        String newSecretKey = "expired-test-secret-key-32-chars-minimum-required";
        keyProvider.rotateKey(newSecretKey);
        keyProvider.setKeyExpiryTime(oldKid, Instant.now().minusSeconds(10));

        // When
        Optional<SecretKey> key = keyProvider.getKeyById(oldKid);

        // Then - Debe verificar expiración antes de retornar
        assertThat(key).isEmpty();
    }

    // ==================== MULTIPLE ROTATION TESTS ====================

    @Test
    @DisplayName("Debe soportar múltiples rotaciones consecutivas")
    void shouldSupportMultipleConsecutiveRotations() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When - Múltiples rotaciones
        String key1 = "rotation-1-secret-key-32-characters-minimum-required";
        String key2 = "rotation-2-secret-key-32-characters-minimum-required";
        String key3 = "rotation-3-secret-key-32-characters-minimum-required";

        keyProvider.rotateKey(key1);
        String kid1 = keyProvider.getCurrentKeyId();

        keyProvider.rotateKey(key2);
        String kid2 = keyProvider.getCurrentKeyId();

        keyProvider.rotateKey(key3);
        String kid3 = keyProvider.getCurrentKeyId();

        // Then - Todos los KIDs deben ser únicos
        assertThat(kid1).isNotEqualTo(kid2);
        assertThat(kid2).isNotEqualTo(kid3);
        assertThat(kid1).isNotEqualTo(kid3);

        // La clave actual debe ser la última
        assertThat(keyProvider.getCurrentKeyId()).isEqualTo(kid3);
    }

    @Test
    @DisplayName("Debe limpiar claves antiguas después de múltiples rotaciones")
    void shouldCleanupOldKeysAfterMultipleRotations() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String originalKid = keyProvider.getCurrentKeyId();

        // When - Múltiples rotaciones con expiración
        for (int i = 0; i < 5; i++) {
            String newKey = "rotation-" + i + "-secret-key-32-chars-minimum-required";
            keyProvider.rotateKey(newKey);
        }

        // Expirar todas las claves antiguas
        keyProvider.setKeyExpiryTime(originalKid, Instant.now().minusSeconds(1));
        keyProvider.cleanupExpiredKeys();

        // Then - Solo debe quedar la clave actual y posiblemente la anterior en gracia
        int activeKeysCount = keyProvider.getActiveKeysCount();
        assertThat(activeKeysCount).isLessThanOrEqualTo(2);
    }

    // ==================== THREAD SAFETY TESTS ====================

    @Test
    @DisplayName("Debe manejar rotación de claves de forma thread-safe")
    void shouldHandleKeyRotationThreadSafely() throws Exception {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When - Rotaciones concurrentes
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String newKey = "concurrent-rotation-" + index + "-32-chars-min-required";
                keyProvider.rotateKey(newKey);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Debe haber una clave actual válida sin errores
        assertThat(keyProvider.getCurrentKey()).isNotNull();
        assertThat(keyProvider.getCurrentKeyId()).isNotNull();
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    @DisplayName("Debe rechazar clave con longitud insuficiente durante rotación")
    void shouldRejectShortKeyDuringRotation() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When & Then
        assertThatThrownBy(() -> keyProvider.rotateKey("short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minimum length");
    }

    @Test
    @DisplayName("Debe rechazar clave nula durante rotación")
    void shouldRejectNullKeyDuringRotation() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When & Then
        assertThatThrownBy(() -> keyProvider.rotateKey(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Debe rechazar clave vacía durante rotación")
    void shouldRejectEmptyKeyDuringRotation() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);

        // When & Then
        assertThatThrownBy(() -> keyProvider.rotateKey(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== KEY ID GENERATION TESTS ====================

    @Test
    @DisplayName("Debe generar Key IDs diferentes para claves diferentes")
    void shouldGenerateDifferentKeyIdsForDifferentKeys() {
        // Given
        when(secretsService.getJwtSecret()).thenReturn(currentSecretKey);
        keyProvider = new JwtKeyProvider(secretsService);
        String kid1 = keyProvider.getCurrentKeyId();

        // When
        String differentKey = "completely-different-secret-key-32-chars-minimum";
        keyProvider.rotateKey(differentKey);
        String kid2 = keyProvider.getCurrentKeyId();

        // Then
        assertThat(kid1).isNotEqualTo(kid2);
    }

    @Test
    @DisplayName("Key ID debe ser reproducible para la misma clave")
    void keyIdShouldBeReproducibleForSameKey() {
        // Given & When
        String secretKey = "test-secret-key-for-reproducibility-32-chars-min";
        String kid1 = JwtKeyProvider.generateKeyId(secretKey);
        String kid2 = JwtKeyProvider.generateKeyId(secretKey);

        // Then
        assertThat(kid1).isEqualTo(kid2);
    }
}

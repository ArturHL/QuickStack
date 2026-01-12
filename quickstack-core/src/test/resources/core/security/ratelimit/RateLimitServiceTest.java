package com.quickstack.core.security.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests para RateLimitService.
 *
 * Este servicio gestiona buckets de rate limiting por clave (IP, email, etc).
 * Usa Bucket4j para implementar el algoritmo Token Bucket.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    // ==================== BUCKET CREATION ====================

    @Test
    @DisplayName("Debe crear bucket con límite de 5 tokens")
    void shouldCreateBucketWithLimit() {
        // Given
        String key = "test-key";
        int capacity = 5;
        int refillTokens = 5;
        long refillMinutes = 15;

        // When
        Bucket bucket = rateLimitService.resolveBucket(key, capacity, refillTokens, refillMinutes);

        // Then
        assertThat(bucket).isNotNull();
        assertThat(bucket.getAvailableTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Debe reutilizar el mismo bucket para la misma clave")
    void shouldReuseBucketForSameKey() {
        // Given
        String key = "same-key";
        int capacity = 5;
        int refillTokens = 5;
        long refillMinutes = 15;

        // When
        Bucket bucket1 = rateLimitService.resolveBucket(key, capacity, refillTokens, refillMinutes);
        Bucket bucket2 = rateLimitService.resolveBucket(key, capacity, refillTokens, refillMinutes);

        // Then - mismo bucket (misma referencia)
        assertThat(bucket1).isSameAs(bucket2);
    }

    @Test
    @DisplayName("Debe crear buckets diferentes para claves diferentes")
    void shouldCreateDifferentBucketsForDifferentKeys() {
        // Given
        String key1 = "key-1";
        String key2 = "key-2";
        int capacity = 5;
        int refillTokens = 5;
        long refillMinutes = 15;

        // When
        Bucket bucket1 = rateLimitService.resolveBucket(key1, capacity, refillTokens, refillMinutes);
        Bucket bucket2 = rateLimitService.resolveBucket(key2, capacity, refillTokens, refillMinutes);

        // Then - buckets diferentes
        assertThat(bucket1).isNotSameAs(bucket2);
    }

    // ==================== TOKEN CONSUMPTION ====================

    @Test
    @DisplayName("Debe permitir consumir tokens hasta el límite")
    void shouldAllowConsumingTokensUpToLimit() {
        // Given
        String key = "consume-key";
        Bucket bucket = rateLimitService.resolveBucket(key, 3, 3, 15);

        // When & Then
        assertThat(bucket.tryConsume(1)).isTrue();  // 1er request
        assertThat(bucket.tryConsume(1)).isTrue();  // 2do request
        assertThat(bucket.tryConsume(1)).isTrue();  // 3er request
        assertThat(bucket.tryConsume(1)).isFalse(); // 4to request - rechazado
    }

    @Test
    @DisplayName("Debe rechazar requests cuando no hay tokens disponibles")
    void shouldRejectWhenNoTokensAvailable() {
        // Given
        String key = "no-tokens-key";
        Bucket bucket = rateLimitService.resolveBucket(key, 1, 1, 15);

        // Consumir el único token
        bucket.tryConsume(1);

        // When
        boolean allowed = bucket.tryConsume(1);

        // Then
        assertThat(allowed).isFalse();
    }

    // ==================== BUCKET CONFIGURATION ====================

    @Test
    @DisplayName("Debe crear bucket con configuración específica de login")
    void shouldCreateLoginBucket() {
        // Given
        String key = "login:192.168.1.1";

        // When - 5 intentos cada 15 minutos
        Bucket bucket = rateLimitService.resolveBucket(key, 5, 5, 15);

        // Then
        assertThat(bucket.getAvailableTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Debe crear bucket con configuración específica de register")
    void shouldCreateRegisterBucket() {
        // Given
        String key = "register:192.168.1.1";

        // When - 3 intentos cada 60 minutos
        Bucket bucket = rateLimitService.resolveBucket(key, 3, 3, 60);

        // Then
        assertThat(bucket.getAvailableTokens()).isEqualTo(3);
    }

    @Test
    @DisplayName("Debe crear bucket con configuración específica de API")
    void shouldCreateApiBucket() {
        // Given
        String key = "api:user-123";

        // When - 100 requests por minuto
        Bucket bucket = rateLimitService.resolveBucket(key, 100, 100, 1);

        // Then
        assertThat(bucket.getAvailableTokens()).isEqualTo(100);
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Debe manejar claves con caracteres especiales")
    void shouldHandleKeysWithSpecialCharacters() {
        // Given
        String key = "email:admin@example.com";

        // When
        Bucket bucket = rateLimitService.resolveBucket(key, 5, 5, 15);

        // Then
        assertThat(bucket).isNotNull();
        assertThat(bucket.getAvailableTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Debe manejar múltiples claves concurrentemente")
    void shouldHandleMultipleKeysConcurrently() {
        // Given
        String key1 = "user-1";
        String key2 = "user-2";
        String key3 = "user-3";

        // When
        Bucket bucket1 = rateLimitService.resolveBucket(key1, 5, 5, 15);
        Bucket bucket2 = rateLimitService.resolveBucket(key2, 5, 5, 15);
        Bucket bucket3 = rateLimitService.resolveBucket(key3, 5, 5, 15);

        // Consumir tokens en diferentes buckets
        bucket1.tryConsume(3);
        bucket2.tryConsume(2);
        bucket3.tryConsume(1);

        // Then - cada bucket mantiene su propio estado
        assertThat(bucket1.getAvailableTokens()).isEqualTo(2);
        assertThat(bucket2.getAvailableTokens()).isEqualTo(3);
        assertThat(bucket3.getAvailableTokens()).isEqualTo(4);
    }
}

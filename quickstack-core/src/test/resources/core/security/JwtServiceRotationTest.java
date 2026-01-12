package com.quickstack.core.security;

import com.quickstack.core.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests para JwtService con soporte de rotación de claves.
 *
 * Cobertura:
 * - Generación de tokens con Key ID (kid) header
 * - Validación de tokens con claves rotadas
 * - Período de gracia de 24 horas
 * - Tokens firmados con clave desconocida fallan
 * - Tokens firmados con clave expirada fallan
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@ExtendWith(MockitoExtension.class)
class JwtServiceRotationTest {

    @Mock
    private JwtKeyProvider keyProvider;

    private JwtService jwtService;

    private UUID userId;
    private UUID tenantId;
    private String email;
    private Role role;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        email = "user@example.com";
        role = Role.USER;

        // Configurar keyProvider con una clave inicial
        String initialSecret = "initial-jwt-secret-key-minimum-32-characters-required";
        when(keyProvider.getCurrentKey()).thenReturn(
            io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                initialSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        );
        when(keyProvider.getCurrentKeyId()).thenReturn("key-id-001");

        jwtService = new JwtService(keyProvider, 3600000L); // 1 hora
    }

    // ==================== TOKEN GENERATION WITH KEY ID TESTS ====================

    @Test
    @DisplayName("Debe generar token con Key ID (kid) en el header")
    void shouldGenerateTokenWithKeyIdHeader() {
        // When
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();

        // Verificar que el header contiene el kid
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3); // header.payload.signature

        String headerJson = new String(
            java.util.Base64.getUrlDecoder().decode(parts[0])
        );
        assertThat(headerJson).contains("\"kid\"");
        assertThat(headerJson).contains("key-id-001");
    }

    @Test
    @DisplayName("Debe incluir kid del JwtKeyProvider actual en cada token")
    void shouldIncludeCurrentKeyIdInEachToken() {
        // Given
        when(keyProvider.getCurrentKeyId()).thenReturn("key-xyz-123");

        // When
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Then
        String headerJson = extractHeader(token);
        assertThat(headerJson).contains("key-xyz-123");
    }

    // ==================== TOKEN VALIDATION WITH ROTATED KEYS TESTS ====================

    @Test
    @DisplayName("Debe validar token firmado con clave actual")
    void shouldValidateTokenSignedWithCurrentKey() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        boolean isValid = jwtService.isTokenValid(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Debe validar token firmado con clave anterior en período de gracia")
    void shouldValidateTokenSignedWithPreviousKeyInGracePeriod() {
        // Given - Generar token con clave actual
        String oldSecret = "old-jwt-secret-key-minimum-32-characters-required-here";
        javax.crypto.SecretKey oldKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            oldSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(oldKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("old-key-001");

        String tokenWithOldKey = jwtService.generateToken(userId, tenantId, email, role);

        // Simular rotación de clave
        String newSecret = "new-jwt-secret-key-minimum-32-characters-required-here";
        javax.crypto.SecretKey newKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            newSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(newKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("new-key-002");

        // La clave antigua debe seguir siendo válida
        when(keyProvider.getKeyById("old-key-001"))
            .thenReturn(java.util.Optional.of(oldKey));
        when(keyProvider.getKeyExpiryTime("old-key-001"))
            .thenReturn(java.util.Optional.of(Instant.now().plusSeconds(24 * 60 * 60)));

        // When
        boolean isValid = jwtService.isTokenValid(tokenWithOldKey);

        // Then - Debe ser válido durante el período de gracia
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar token firmado con clave expirada")
    void shouldRejectTokenSignedWithExpiredKey() {
        // Given - Token con clave antigua
        String oldSecret = "expired-key-secret-minimum-32-characters-required";
        javax.crypto.SecretKey oldKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            oldSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(oldKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("expired-key-001");

        String tokenWithExpiredKey = jwtService.generateToken(userId, tenantId, email, role);

        // Rotar a nueva clave y expirar la antigua
        String newSecret = "new-active-secret-key-32-characters-minimum-required";
        javax.crypto.SecretKey newKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            newSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(newKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("new-key-002");

        // La clave antigua ya expiró (más de 24 horas)
        when(keyProvider.getKeyById("expired-key-001"))
            .thenReturn(java.util.Optional.empty()); // Ya fue limpiada

        // When & Then
        assertThat(jwtService.isTokenValid(tokenWithExpiredKey)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar token firmado con clave desconocida")
    void shouldRejectTokenSignedWithUnknownKey() {
        // Given - Token firmado con una clave que nunca estuvo en el sistema
        String unknownSecret = "unknown-key-secret-32-characters-minimum-required";
        javax.crypto.SecretKey unknownKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            unknownSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        // Crear token manualmente con clave desconocida
        String maliciousToken = io.jsonwebtoken.Jwts.builder()
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .header().keyId("malicious-key-999").and()
            .issuedAt(new java.util.Date())
            .expiration(new java.util.Date(System.currentTimeMillis() + 3600000))
            .signWith(unknownKey)
            .compact();

        // El keyProvider no conoce esta clave
        when(keyProvider.getKeyById("malicious-key-999"))
            .thenReturn(java.util.Optional.empty());

        // When & Then
        assertThat(jwtService.isTokenValid(maliciousToken)).isFalse();
    }

    // ==================== GRACE PERIOD TESTS ====================

    @Test
    @DisplayName("Tokens con clave anterior deben ser válidos exactamente durante 24 horas")
    void tokensShouldBeValidForExactly24Hours() {
        // Given - Token con clave antigua
        String oldSecret = "grace-period-test-key-32-characters-minimum-required";
        javax.crypto.SecretKey oldKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            oldSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(oldKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("grace-key-001");

        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Rotar clave
        String newSecret = "new-key-after-rotation-32-characters-minimum-required";
        javax.crypto.SecretKey newKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            newSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(newKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("new-key-002");

        // Simular 23 horas después (aún válido)
        Instant almostExpired = Instant.now().plusSeconds((24 * 60 * 60) - 60);
        when(keyProvider.getKeyById("grace-key-001"))
            .thenReturn(java.util.Optional.of(oldKey));
        when(keyProvider.getKeyExpiryTime("grace-key-001"))
            .thenReturn(java.util.Optional.of(almostExpired));

        // When - Verificar antes de expirar
        boolean validBefore = jwtService.isTokenValid(token);

        // Simular 24 horas + 1 segundo (expirado)
        Instant expired = Instant.now().minusSeconds(1);
        when(keyProvider.getKeyExpiryTime("grace-key-001"))
            .thenReturn(java.util.Optional.of(expired));

        // When - Verificar después de expirar
        boolean validAfter = jwtService.isTokenValid(token);

        // Then
        assertThat(validBefore).isTrue();
        assertThat(validAfter).isFalse();
    }

    // ==================== CLAIMS EXTRACTION WITH ROTATION TESTS ====================

    @Test
    @DisplayName("Debe extraer claims de token firmado con clave rotada")
    void shouldExtractClaimsFromTokenWithRotatedKey() {
        // Given - Token con clave antigua
        String oldSecret = "claims-extraction-test-key-32-chars-minimum-required";
        javax.crypto.SecretKey oldKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            oldSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(oldKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("claims-key-001");

        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Rotar clave
        String newSecret = "new-claims-test-key-32-characters-minimum-required";
        javax.crypto.SecretKey newKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            newSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(newKey);
        when(keyProvider.getCurrentKeyId()).thenReturn("new-key-002");

        // Mantener clave antigua disponible
        when(keyProvider.getKeyById("claims-key-001"))
            .thenReturn(java.util.Optional.of(oldKey));
        when(keyProvider.getKeyExpiryTime("claims-key-001"))
            .thenReturn(java.util.Optional.of(Instant.now().plusSeconds(24 * 60 * 60)));

        // When
        UUID extractedUserId = jwtService.extractUserId(token);
        UUID extractedTenantId = jwtService.extractTenantId(token);
        String extractedEmail = jwtService.extractEmail(token);
        Role extractedRole = jwtService.extractRole(token);

        // Then
        assertThat(extractedUserId).isEqualTo(userId);
        assertThat(extractedTenantId).isEqualTo(tenantId);
        assertThat(extractedEmail).isEqualTo(email);
        assertThat(extractedRole).isEqualTo(role);
    }

    // ==================== MULTIPLE ROTATIONS TESTS ====================

    @Test
    @DisplayName("Debe manejar múltiples rotaciones correctamente")
    void shouldHandleMultipleRotationsCorrectly() {
        // Given - Primera clave
        String key1 = "rotation-1-secret-key-32-characters-minimum-required";
        javax.crypto.SecretKey secretKey1 = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            key1.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(secretKey1);
        when(keyProvider.getCurrentKeyId()).thenReturn("key-001");

        String token1 = jwtService.generateToken(userId, tenantId, email, role);

        // Segunda rotación
        String key2 = "rotation-2-secret-key-32-characters-minimum-required";
        javax.crypto.SecretKey secretKey2 = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            key2.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(secretKey2);
        when(keyProvider.getCurrentKeyId()).thenReturn("key-002");

        String token2 = jwtService.generateToken(userId, tenantId, email, role);

        // Tercera rotación
        String key3 = "rotation-3-secret-key-32-characters-minimum-required";
        javax.crypto.SecretKey secretKey3 = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            key3.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(keyProvider.getCurrentKey()).thenReturn(secretKey3);
        when(keyProvider.getCurrentKeyId()).thenReturn("key-003");

        // Mantener claves anteriores disponibles en gracia
        Instant gracePeriod = Instant.now().plusSeconds(24 * 60 * 60);
        when(keyProvider.getKeyById("key-001")).thenReturn(java.util.Optional.of(secretKey1));
        when(keyProvider.getKeyExpiryTime("key-001")).thenReturn(java.util.Optional.of(gracePeriod));
        when(keyProvider.getKeyById("key-002")).thenReturn(java.util.Optional.of(secretKey2));
        when(keyProvider.getKeyExpiryTime("key-002")).thenReturn(java.util.Optional.of(gracePeriod));

        // When & Then - Todos los tokens deben ser válidos
        assertThat(jwtService.isTokenValid(token1)).isTrue();
        assertThat(jwtService.isTokenValid(token2)).isTrue();
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("Debe manejar token sin kid header gracefully")
    void shouldHandleTokenWithoutKidHeaderGracefully() {
        // Given - Token antiguo sin kid header
        String legacySecret = "legacy-secret-key-without-kid-32-chars-minimum";
        javax.crypto.SecretKey legacyKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            legacySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String legacyToken = io.jsonwebtoken.Jwts.builder()
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .issuedAt(new java.util.Date())
            .expiration(new java.util.Date(System.currentTimeMillis() + 3600000))
            .signWith(legacyKey)
            .compact();

        // When & Then - Debe fallar o usar clave por defecto
        // (Dependiendo de la implementación)
        assertThat(jwtService.isTokenValid(legacyToken)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar token con firma inválida incluso con kid correcto")
    void shouldRejectTokenWithInvalidSignatureEvenWithCorrectKid() {
        // Given - Token válido
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Modificar el payload (invalidar firma)
        String[] parts = token.split("\\.");
        String tamperedPayload = new String(java.util.Base64.getUrlEncoder().encode(
            "{\"sub\":\"tampered\"}".getBytes()
        ));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        // When & Then
        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    // ==================== HELPER METHODS ====================

    private String extractHeader(String token) {
        String[] parts = token.split("\\.");
        return new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
    }
}

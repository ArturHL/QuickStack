package com.quickstack.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.quickstack.core.user.Role;

/**
 * Tests para JwtService.
 * Prueba generación y validación de tokens JWT.
 */
class JwtServiceTest {

    private JwtService jwtService;

    private UUID userId;
    private UUID tenantId;
    private String email;
    private Role role;

    @BeforeEach
    void setUp() {
        // Secret key para tests (mínimo 256 bits para HS256)
        String secretKey = "mi-clave-secreta-super-segura-para-tests-debe-ser-larga-256-bits";
        long expirationMs = 3600000; // 1 hora

        jwtService = new JwtService(secretKey, expirationMs);

        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        email = "test@example.com";
        role = Role.USER;
    }

    @Test
    @DisplayName("Debe generar un token JWT válido")
    void shouldGenerateValidToken() {
        // When
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("Debe extraer userId del token")
    void shouldExtractUserIdFromToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        UUID extractedUserId = jwtService.extractUserId(token);

        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("Debe extraer tenantId del token")
    void shouldExtractTenantIdFromToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        UUID extractedTenantId = jwtService.extractTenantId(token);

        // Then
        assertThat(extractedTenantId).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Debe extraer email del token")
    void shouldExtractEmailFromToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        String extractedEmail = jwtService.extractEmail(token);

        // Then
        assertThat(extractedEmail).isEqualTo(email);
    }

    @Test
    @DisplayName("Debe extraer role del token")
    void shouldExtractRoleFromToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        Role extractedRole = jwtService.extractRole(token);

        // Then
        assertThat(extractedRole).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Debe validar token correctamente")
    void shouldValidateToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);

        // When
        boolean isValid = jwtService.isTokenValid(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar token inválido")
    void shouldRejectInvalidToken() {
        // Given
        String invalidToken = "token.invalido.aqui";

        // When
        boolean isValid = jwtService.isTokenValid(invalidToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar token manipulado")
    void shouldRejectTamperedToken() {
        // Given
        String token = jwtService.generateToken(userId, tenantId, email, role);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // When
        boolean isValid = jwtService.isTokenValid(tamperedToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar token expirado")
    void shouldRejectExpiredToken() {
        // Given - JwtService con expiración de 1ms
        JwtService shortLivedJwtService = new JwtService(
                "mi-clave-secreta-super-segura-para-tests-debe-ser-larga-256-bits",
                1 // 1 milisegundo
        );
        String token = shortLivedJwtService.generateToken(userId, tenantId, email, role);

        // Esperar a que expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        boolean isValid = shortLivedJwtService.isTokenValid(token);

        // Then
        assertThat(isValid).isFalse();
    }
}

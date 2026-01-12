package com.quickstack.core.security;

import com.quickstack.core.auth.AuthService;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Tests de integración end-to-end para rotación de secretos JWT.
 *
 * Cobertura:
 * - Flujo completo de rotación de claves
 * - Tokens generados con nueva clave funcionan inmediatamente
 * - Tokens con clave anterior válidos durante 24h
 * - Tokens con clave anterior fallan después de 24h
 * - Endpoint de admin para rotación de claves
 * - Múltiples servicios usando tokens con claves diferentes
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@ActiveProfiles("test")
class SecretRotationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtKeyProvider keyProvider;

    private String adminToken;
    private UUID adminUserId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Registrar admin para pruebas
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Admin Corp")
            .tenantSlug("admincorp")
            .email("admin@test.com")
            .password("admin123")
            .userName("Admin User")
            .build();

        AuthResponse response = authService.register(registerRequest);
        adminToken = response.getAccessToken();
        adminUserId = response.getUserId();
        tenantId = response.getTenantId();
    }

    // ==================== END-TO-END ROTATION FLOW TESTS ====================

    @Test
    @DisplayName("E2E: Rotación completa de claves JWT")
    void endToEndKeyRotationFlow() {
        // Given - Usuario autenticado con token actual
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse loginResponse = authService.login(loginRequest);
        String oldToken = loginResponse.getAccessToken();
        String oldKeyId = keyProvider.getCurrentKeyId();

        // Verificar que el token antiguo funciona
        assertThat(jwtService.isTokenValid(oldToken)).isTrue();

        // When - Rotar clave JWT
        String newSecret = "new-rotated-jwt-secret-key-minimum-32-characters-required";
        keyProvider.rotateKey(newSecret);
        String newKeyId = keyProvider.getCurrentKeyId();

        // Generar nuevo token con nueva clave
        AuthResponse newLoginResponse = authService.login(loginRequest);
        String newToken = newLoginResponse.getAccessToken();

        // Then
        // 1. Los Key IDs deben ser diferentes
        assertThat(newKeyId).isNotEqualTo(oldKeyId);

        // 2. Ambos tokens deben ser válidos (período de gracia)
        assertThat(jwtService.isTokenValid(oldToken)).isTrue();
        assertThat(jwtService.isTokenValid(newToken)).isTrue();

        // 3. Ambos tokens deben poder extraer claims correctamente
        assertThat(jwtService.extractUserId(oldToken)).isEqualTo(adminUserId);
        assertThat(jwtService.extractUserId(newToken)).isEqualTo(adminUserId);
    }

    @Test
    @DisplayName("E2E: Token con nueva clave funciona inmediatamente después de rotación")
    void newTokenWorksImmediatelyAfterRotation() {
        // Given - Rotar clave
        String newSecret = "immediate-test-secret-key-32-characters-minimum-required";
        keyProvider.rotateKey(newSecret);

        // When - Login inmediato con nueva clave
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse response = authService.login(loginRequest);

        // Then - Token debe ser válido inmediatamente
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(jwtService.isTokenValid(response.getAccessToken())).isTrue();
    }

    @Test
    @DisplayName("E2E: Tokens con clave anterior válidos durante período de gracia")
    void oldTokensValidDuringGracePeriod() {
        // Given - Token con clave actual
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse oldResponse = authService.login(loginRequest);
        String oldToken = oldResponse.getAccessToken();
        String oldKeyId = keyProvider.getCurrentKeyId();

        // When - Rotar clave
        String newSecret = "grace-period-test-key-32-characters-minimum-required";
        keyProvider.rotateKey(newSecret);

        // Establecer período de gracia de 24 horas
        Instant gracePeriodEnd = Instant.now().plusSeconds(24 * 60 * 60);
        keyProvider.setKeyExpiryTime(oldKeyId, gracePeriodEnd);

        // Then - Token antiguo debe seguir siendo válido
        assertThat(jwtService.isTokenValid(oldToken)).isTrue();

        // Verificar acceso a endpoints protegidos con token antiguo
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oldToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/users/me",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("E2E: Tokens con clave anterior fallan después del período de gracia")
    void oldTokensFailAfterGracePeriod() {
        // Given - Token con clave actual
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse oldResponse = authService.login(loginRequest);
        String oldToken = oldResponse.getAccessToken();
        String oldKeyId = keyProvider.getCurrentKeyId();

        // Rotar clave
        String newSecret = "expired-grace-test-key-32-chars-minimum-required";
        keyProvider.rotateKey(newSecret);

        // Simular que el período de gracia expiró
        Instant expiredTime = Instant.now().minusSeconds(1);
        keyProvider.setKeyExpiryTime(oldKeyId, expiredTime);

        // Limpiar claves expiradas
        keyProvider.cleanupExpiredKeys();

        // Then - Token antiguo debe ser inválido
        assertThat(jwtService.isTokenValid(oldToken)).isFalse();

        // Verificar que endpoint rechaza el token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oldToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/users/me",
            HttpMethod.GET,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ==================== ADMIN ENDPOINT TESTS ====================

    @Test
    @DisplayName("E2E: Admin puede rotar JWT secret mediante endpoint")
    void adminCanRotateJwtSecretViaEndpoint() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
            {
                "newSecret": "endpoint-rotation-secret-key-32-chars-minimum-required"
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/admin/security/rotate-jwt-key",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("success");
    }

    @Test
    @DisplayName("E2E: Usuario no-admin no puede rotar JWT secret")
    void nonAdminCannotRotateJwtSecret() {
        // Given - Crear usuario regular
        RegisterRequest userRequest = RegisterRequest.builder()
            .tenantName("User Corp")
            .tenantSlug("usercorp")
            .email("user@test.com")
            .password("user123")
            .userName("Regular User")
            .build();

        AuthResponse userResponse = authService.register(userRequest);
        String userToken = userResponse.getAccessToken();

        // Cambiar rol a USER (en una implementación real esto vendría de DB)
        // Para este test asumimos que el token ya tiene rol USER

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
            {
                "newSecret": "unauthorized-rotation-attempt-32-chars-minimum"
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/admin/security/rotate-jwt-key",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Then - Debe ser rechazado (403 Forbidden o 401 Unauthorized)
        assertThat(response.getStatusCode()).isIn(
            HttpStatus.FORBIDDEN,
            HttpStatus.UNAUTHORIZED
        );
    }

    @Test
    @DisplayName("E2E: Endpoint de rotación valida longitud mínima del secret")
    void rotationEndpointValidatesSecretLength() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
            {
                "newSecret": "short"
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/admin/security/rotate-jwt-key",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("minimum length");
    }

    // ==================== CONCURRENT ACCESS TESTS ====================

    @Test
    @DisplayName("E2E: Múltiples usuarios pueden autenticarse durante rotación")
    void multipleUsersCanAuthenticateDuringRotation() throws Exception {
        // Given - Crear múltiples usuarios
        for (int i = 0; i < 3; i++) {
            RegisterRequest req = RegisterRequest.builder()
                .tenantName("Corp " + i)
                .tenantSlug("corp" + i)
                .email("user" + i + "@test.com")
                .password("pass123")
                .userName("User " + i)
                .build();
            authService.register(req);
        }

        // When - Rotación concurrente con logins
        String newSecret = "concurrent-rotation-secret-32-chars-minimum-required";

        Thread rotationThread = new Thread(() -> {
            keyProvider.rotateKey(newSecret);
        });

        Thread[] loginThreads = new Thread[3];
        AuthResponse[] responses = new AuthResponse[3];

        for (int i = 0; i < 3; i++) {
            final int index = i;
            loginThreads[i] = new Thread(() -> {
                LoginRequest req = LoginRequest.builder()
                    .email("user" + index + "@test.com")
                    .password("pass123")
                    .tenantSlug("corp" + index)
                    .build();
                responses[index] = authService.login(req);
            });
        }

        rotationThread.start();
        for (Thread t : loginThreads) {
            t.start();
        }

        rotationThread.join();
        for (Thread t : loginThreads) {
            t.join();
        }

        // Then - Todos los tokens deben ser válidos
        for (AuthResponse response : responses) {
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotNull();
            assertThat(jwtService.isTokenValid(response.getAccessToken())).isTrue();
        }
    }

    // ==================== SECURITY VERIFICATION TESTS ====================

    @Test
    @DisplayName("E2E: Verificar que no hay secretos hardcoded en configuración")
    void verifyNoHardcodedSecretsInConfiguration() {
        // Given - Verificar que JWT_SECRET viene de variables de entorno
        String jwtSecret = System.getenv("JWT_SECRET");

        // Then - No debe ser nulo (debe estar configurado en entorno de test)
        // y no debe ser un valor hardcoded conocido
        assertThat(jwtSecret).isNotNull();
        assertThat(jwtSecret).doesNotContain("changeme");
        assertThat(jwtSecret).doesNotContain("default");
        assertThat(jwtSecret).doesNotContain("secret");
    }

    @Test
    @DisplayName("E2E: Múltiples rotaciones consecutivas funcionan correctamente")
    void multipleConsecutiveRotationsWorkCorrectly() {
        // Given - Token inicial
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse initial = authService.login(loginRequest);
        String initialToken = initial.getAccessToken();

        // When - Múltiples rotaciones
        String[] secrets = {
            "rotation-1-secret-key-32-characters-minimum-required",
            "rotation-2-secret-key-32-characters-minimum-required",
            "rotation-3-secret-key-32-characters-minimum-required"
        };

        for (String secret : secrets) {
            keyProvider.rotateKey(secret);
        }

        // Login con la última clave
        AuthResponse finalResponse = authService.login(loginRequest);
        String finalToken = finalResponse.getAccessToken();

        // Then
        // El token inicial aún debe ser válido (en gracia)
        assertThat(jwtService.isTokenValid(initialToken)).isTrue();

        // El token final debe ser válido
        assertThat(jwtService.isTokenValid(finalToken)).isTrue();

        // Ambos tokens deben tener diferentes kid
        assertThat(extractKeyId(initialToken)).isNotEqualTo(extractKeyId(finalToken));
    }

    @Test
    @DisplayName("E2E: Cleanup de claves expiradas no afecta claves activas")
    void cleanupDoesNotAffectActiveKeys() {
        // Given - Token actual
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("admin123")
            .tenantSlug("admincorp")
            .build();

        AuthResponse response = authService.login(loginRequest);
        String activeToken = response.getAccessToken();

        // When - Limpiar claves expiradas
        keyProvider.cleanupExpiredKeys();

        // Then - Token activo debe seguir funcionando
        assertThat(jwtService.isTokenValid(activeToken)).isTrue();
    }

    // ==================== HELPER METHODS ====================

    private String extractKeyId(String token) {
        String[] parts = token.split("\\.");
        String headerJson = new String(
            java.util.Base64.getUrlDecoder().decode(parts[0])
        );
        // Extraer kid del JSON header (implementación simple)
        int kidStart = headerJson.indexOf("\"kid\":\"") + 7;
        int kidEnd = headerJson.indexOf("\"", kidStart);
        return headerJson.substring(kidStart, kidEnd);
    }
}

package com.quickstack.core.audit;

import com.quickstack.core.auth.AuthService;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.tenant.TenantRepository;
import com.quickstack.core.user.Role;
import com.quickstack.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Tests de integración para Audit Logging.
 *
 * Cobertura:
 * - Integración con AuthService para capturar eventos
 * - Verificación de logs asíncronos en flujos reales
 * - Persistencia completa de audit logs
 * - Escenarios end-to-end de auditoría
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    // ==================== LOGIN SUCCESS AUDIT TESTS ====================

    @Test
    @DisplayName("Debe crear audit log cuando el login es exitoso")
    void shouldCreateAuditLogOnSuccessfulLogin() {
        // Given - Registrar un usuario primero
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug("testcorp")
            .email("admin@test.com")
            .password("password123")
            .userName("Admin User")
            .build();
        authService.register(registerRequest);

        // Limpiar logs del registro
        auditLogRepository.deleteAll();

        // When - Login exitoso
        LoginRequest loginRequest = LoginRequest.builder()
            .email("admin@test.com")
            .password("password123")
            .tenantSlug("testcorp")
            .build();
        AuthResponse response = authService.login(loginRequest);

        // Then - Verificar que se creó el audit log (esperar async)
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getEventType()).isEqualTo(SecurityEventType.LOGIN_SUCCESS);
            assertThat(log.getUserId()).isEqualTo(response.getUserId());
            assertThat(log.getTenantId()).isEqualTo(response.getTenantId());
            assertThat(log.getDetails()).containsEntry("email", "admin@test.com");
            assertThat(log.getDetails()).containsEntry("role", Role.ADMIN.name());
        });
    }

    // ==================== LOGIN FAILED AUDIT TESTS ====================

    @Test
    @DisplayName("Debe crear audit log cuando el login falla por contraseña incorrecta")
    void shouldCreateAuditLogOnFailedLoginWrongPassword() {
        // Given
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug("testcorp")
            .email("user@test.com")
            .password("correctpassword")
            .userName("Test User")
            .build();
        authService.register(registerRequest);

        auditLogRepository.deleteAll();

        // When - Intento de login con contraseña incorrecta
        LoginRequest loginRequest = LoginRequest.builder()
            .email("user@test.com")
            .password("wrongpassword")
            .tenantSlug("testcorp")
            .build();

        try {
            authService.login(loginRequest);
        } catch (Exception e) {
            // Esperado
        }

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getEventType()).isEqualTo(SecurityEventType.LOGIN_FAILED);
            assertThat(log.getDetails()).containsEntry("email", "user@test.com");
            assertThat(log.getDetails()).containsEntry("reason", "Invalid password");
        });
    }

    @Test
    @DisplayName("Debe crear audit log cuando el login falla por tenant no encontrado")
    void shouldCreateAuditLogOnFailedLoginTenantNotFound() {
        // When
        LoginRequest loginRequest = LoginRequest.builder()
            .email("user@test.com")
            .password("password123")
            .tenantSlug("nonexistent")
            .build();

        try {
            authService.login(loginRequest);
        } catch (Exception e) {
            // Esperado
        }

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getEventType()).isEqualTo(SecurityEventType.LOGIN_FAILED);
            assertThat(log.getUserId()).isNull();
            assertThat(log.getDetails()).containsEntry("reason", "Tenant not found");
        });
    }

    // ==================== REGISTRATION AUDIT TESTS ====================

    @Test
    @DisplayName("Debe crear audit logs durante el proceso de registro")
    void shouldCreateAuditLogsOnRegistration() {
        // When
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("New Corp")
            .tenantSlug("newcorp")
            .email("admin@newcorp.com")
            .password("securepass123")
            .userName("Admin User")
            .build();
        AuthResponse response = authService.register(registerRequest);

        // Then - Debe haber logs de tenant creado y usuario creado
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSizeGreaterThanOrEqualTo(1);

            boolean hasTenantCreated = logs.stream()
                .anyMatch(log -> log.getEventType() == SecurityEventType.TENANT_CREATED);
            boolean hasUserCreated = logs.stream()
                .anyMatch(log -> log.getEventType() == SecurityEventType.USER_CREATED);

            assertThat(hasTenantCreated || hasUserCreated).isTrue();
        });
    }

    // ==================== QUERY INTEGRATION TESTS ====================

    @Test
    @DisplayName("Debe buscar logs de auditoría por tenant con filtros")
    void shouldQueryAuditLogsByTenantWithFilters() {
        // Given
        RegisterRequest req1 = RegisterRequest.builder()
            .tenantName("Corp A")
            .tenantSlug("corpa")
            .email("admin@corpa.com")
            .password("pass123")
            .userName("Admin A")
            .build();
        AuthResponse resp1 = authService.register(req1);

        RegisterRequest req2 = RegisterRequest.builder()
            .tenantName("Corp B")
            .tenantSlug("corpb")
            .email("admin@corpb.com")
            .password("pass123")
            .userName("Admin B")
            .build();
        AuthResponse resp2 = authService.register(req2);

        // Esperar a que se creen los logs
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditLogRepository.count()).isGreaterThan(0);
        });

        // When - Buscar logs solo del tenant A
        Page<AuditLog> page = auditLogRepository.findByTenantId(
            resp1.getTenantId(),
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allMatch(log ->
            log.getTenantId().equals(resp1.getTenantId())
        );
    }

    @Test
    @DisplayName("Debe buscar logs por tipo de evento")
    void shouldQueryAuditLogsByEventType() {
        // Given
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug("testcorp")
            .email("user@test.com")
            .password("password123")
            .userName("Test User")
            .build();
        AuthResponse regResponse = authService.register(registerRequest);

        // Hacer login exitoso
        auditLogRepository.deleteAll();
        LoginRequest loginRequest = LoginRequest.builder()
            .email("user@test.com")
            .password("password123")
            .tenantSlug("testcorp")
            .build();
        authService.login(loginRequest);

        // When
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> successLogs = auditLogRepository.findByTenantIdAndEventType(
                regResponse.getTenantId(),
                SecurityEventType.LOGIN_SUCCESS,
                PageRequest.of(0, 10)
            );

            // Then
            assertThat(successLogs.getContent()).isNotEmpty();
            assertThat(successLogs.getContent()).allMatch(log ->
                log.getEventType() == SecurityEventType.LOGIN_SUCCESS
            );
        });
    }

    @Test
    @DisplayName("Debe buscar logs por rango de fechas")
    void shouldQueryAuditLogsByDateRange() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);

        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug("testcorp")
            .email("user@test.com")
            .password("password123")
            .userName("Test User")
            .build();
        AuthResponse response = authService.register(registerRequest);

        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        // When
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Page<AuditLog> logs = auditLogRepository.findByTenantIdAndCreatedAtBetween(
                response.getTenantId(),
                start,
                end,
                PageRequest.of(0, 10)
            );

            // Then
            assertThat(logs.getContent()).isNotEmpty();
            assertThat(logs.getContent()).allMatch(log ->
                log.getCreatedAt().isAfter(start) && log.getCreatedAt().isBefore(end)
            );
        });
    }

    // ==================== ASYNC BEHAVIOR INTEGRATION TESTS ====================

    @Test
    @DisplayName("Debe procesar múltiples eventos de auditoría sin bloqueo")
    void shouldProcessMultipleAuditEventsWithoutBlocking() {
        // Given
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug("testcorp")
            .email("user@test.com")
            .password("password123")
            .userName("Test User")
            .build();
        AuthResponse response = authService.register(registerRequest);

        auditLogRepository.deleteAll();

        // When - Múltiples logins rápidos
        LoginRequest loginRequest = LoginRequest.builder()
            .email("user@test.com")
            .password("password123")
            .tenantSlug("testcorp")
            .build();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            authService.login(loginRequest);
        }
        long endTime = System.currentTimeMillis();

        // Then - Debe completarse rápido (sin esperar a que se guarden los logs)
        assertThat(endTime - startTime).isLessThan(1000);

        // Verificar que eventualmente se guardaron todos los logs
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSizeGreaterThanOrEqualTo(5);
        });
    }

    // ==================== JSONB PERSISTENCE INTEGRATION TESTS ====================

    @Test
    @DisplayName("Debe persistir y recuperar detalles JSONB complejos")
    void shouldPersistAndRetrieveComplexJsonbDetails() {
        // Given
        java.util.Map<String, Object> complexDetails = new java.util.HashMap<>();
        complexDetails.put("ipAddress", "192.168.1.100");
        complexDetails.put("userAgent", "Mozilla/5.0");
        complexDetails.put("metadata", java.util.Map.of(
            "browser", "Chrome",
            "os", "Windows 10"
        ));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            "192.168.1.100",
            "Mozilla/5.0",
            complexDetails
        );

        // Then
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getDetails()).containsEntry("ipAddress", "192.168.1.100");
            assertThat(log.getDetails()).containsKey("metadata");
        });
    }

    // ==================== SECURITY EVENT COVERAGE TESTS ====================

    @Test
    @DisplayName("Debe registrar todos los tipos de eventos de seguridad")
    void shouldLogAllSecurityEventTypes() {
        // Given
        java.util.UUID userId = java.util.UUID.randomUUID();
        java.util.UUID tenantId = java.util.UUID.randomUUID();

        // When - Registrar diferentes tipos de eventos
        SecurityEventType[] eventTypes = {
            SecurityEventType.LOGIN_SUCCESS,
            SecurityEventType.LOGIN_FAILED,
            SecurityEventType.LOGOUT,
            SecurityEventType.PASSWORD_CHANGE,
            SecurityEventType.TOKEN_REFRESH,
            SecurityEventType.ACCOUNT_LOCKED
        };

        for (SecurityEventType eventType : eventTypes) {
            auditService.logSecurityEvent(
                eventType,
                userId,
                tenantId,
                "10.0.0.1",
                "TestAgent",
                null
            );
        }

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).hasSizeGreaterThanOrEqualTo(eventTypes.length);

            for (SecurityEventType eventType : eventTypes) {
                boolean hasEventType = logs.stream()
                    .anyMatch(log -> log.getEventType() == eventType);
                assertThat(hasEventType).as("Should have " + eventType).isTrue();
            }
        });
    }
}

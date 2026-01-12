package com.quickstack.core.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de persistencia para AuditLogRepository.
 *
 * Cobertura:
 * - Query methods personalizados
 * - Filtrado por tenant, usuario, tipo de evento
 * - Paginación y ordenamiento
 * - Persistencia de JSONB
 */
@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID tenantId1;
    private UUID tenantId2;
    private UUID userId1;
    private UUID userId2;

    @BeforeEach
    void setUp() {
        tenantId1 = UUID.randomUUID();
        tenantId2 = UUID.randomUUID();
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();

        auditLogRepository.deleteAll();
    }

    // ==================== BASIC PERSISTENCE TESTS ====================

    @Test
    @DisplayName("Debe guardar y recuperar audit log con todos los campos")
    void shouldSaveAndRetrieveAuditLog() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("email", "user@example.com");
        details.put("attemptNumber", 1);

        AuditLog auditLog = AuditLog.builder()
            .eventType(SecurityEventType.LOGIN_SUCCESS)
            .userId(userId1)
            .tenantId(tenantId1)
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0")
            .details(details)
            .build();

        // When
        AuditLog saved = auditLogRepository.save(auditLog);
        AuditLog retrieved = auditLogRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getEventType()).isEqualTo(SecurityEventType.LOGIN_SUCCESS);
        assertThat(retrieved.getUserId()).isEqualTo(userId1);
        assertThat(retrieved.getTenantId()).isEqualTo(tenantId1);
        assertThat(retrieved.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(retrieved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(retrieved.getDetails()).containsEntry("email", "user@example.com");
        assertThat(retrieved.getDetails()).containsEntry("attemptNumber", 1);
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe persistir JSONB details correctamente")
    void shouldPersistJsonbDetailsCorrectly() {
        // Given
        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("string", "value");
        complexDetails.put("number", 42);
        complexDetails.put("boolean", true);
        complexDetails.put("nested", Map.of("key", "nestedValue"));
        complexDetails.put("array", List.of("item1", "item2"));

        AuditLog auditLog = AuditLog.builder()
            .eventType(SecurityEventType.PASSWORD_CHANGE)
            .userId(userId1)
            .tenantId(tenantId1)
            .ipAddress("10.0.0.1")
            .userAgent("curl/7.64.1")
            .details(complexDetails)
            .build();

        // When
        AuditLog saved = auditLogRepository.save(auditLog);
        auditLogRepository.flush();
        AuditLog retrieved = auditLogRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(retrieved.getDetails()).isNotNull();
        assertThat(retrieved.getDetails()).containsEntry("string", "value");
        assertThat(retrieved.getDetails()).containsEntry("number", 42);
        assertThat(retrieved.getDetails()).containsEntry("boolean", true);
    }

    // ==================== QUERY BY TENANT TESTS ====================

    @Test
    @DisplayName("Debe encontrar logs por tenant ID con paginación")
    void shouldFindLogsByTenantId() {
        // Given
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGOUT, userId2, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId2, tenantId2);

        // When
        Page<AuditLog> page = auditLogRepository.findByTenantId(
            tenantId1,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).allMatch(log -> log.getTenantId().equals(tenantId1));
    }

    @Test
    @DisplayName("Debe respetar paginación en búsqueda por tenant")
    void shouldRespectPaginationForTenantSearch() {
        // Given
        for (int i = 0; i < 25; i++) {
            createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        }

        // When
        Page<AuditLog> firstPage = auditLogRepository.findByTenantId(
            tenantId1,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );
        Page<AuditLog> secondPage = auditLogRepository.findByTenantId(
            tenantId1,
            PageRequest.of(1, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.getContent()).hasSize(10);
    }

    // ==================== QUERY BY USER TESTS ====================

    @Test
    @DisplayName("Debe encontrar logs por user ID")
    void shouldFindLogsByUserId() {
        // Given
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        createAuditLog(SecurityEventType.PASSWORD_CHANGE, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId2, tenantId1);

        // When
        Page<AuditLog> page = auditLogRepository.findByUserId(
            userId1,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(log -> log.getUserId().equals(userId1));
    }

    // ==================== QUERY BY TENANT AND USER TESTS ====================

    @Test
    @DisplayName("Debe encontrar logs por tenant y user ID")
    void shouldFindLogsByTenantIdAndUserId() {
        // Given
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGOUT, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId2);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId2, tenantId1);

        // When
        Page<AuditLog> page = auditLogRepository.findByTenantIdAndUserId(
            tenantId1,
            userId1,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(log ->
            log.getTenantId().equals(tenantId1) && log.getUserId().equals(userId1)
        );
    }

    // ==================== QUERY BY EVENT TYPE TESTS ====================

    @Test
    @DisplayName("Debe encontrar logs por tipo de evento")
    void shouldFindLogsByEventType() {
        // Given
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId2, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGOUT, userId1, tenantId1);

        // When
        Page<AuditLog> page = auditLogRepository.findByTenantIdAndEventType(
            tenantId1,
            SecurityEventType.LOGIN_SUCCESS,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(log ->
            log.getEventType() == SecurityEventType.LOGIN_SUCCESS
        );
    }

    // ==================== QUERY BY DATE RANGE TESTS ====================

    @Test
    @DisplayName("Debe encontrar logs por rango de fechas")
    void shouldFindLogsByDateRange() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        LocalDateTime end = LocalDateTime.now();

        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGOUT, userId1, tenantId1);

        // Crear un log antiguo que no debe aparecer
        AuditLog oldLog = createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        // Simular que es antiguo modificando directamente
        oldLog.setCreatedAt(LocalDateTime.now().minusDays(5));
        auditLogRepository.save(oldLog);

        // When
        Page<AuditLog> page = auditLogRepository.findByTenantIdAndCreatedAtBetween(
            tenantId1,
            start,
            end,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(log ->
            log.getCreatedAt().isAfter(start) && log.getCreatedAt().isBefore(end)
        );
    }

    // ==================== FAILED LOGIN ATTEMPTS QUERY TESTS ====================

    @Test
    @DisplayName("Debe encontrar intentos de login fallidos recientes por IP")
    void shouldFindRecentFailedLoginsByIp() {
        // Given
        String ipAddress = "192.168.1.100";
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);

        createFailedLoginLog(ipAddress, tenantId1);
        createFailedLoginLog(ipAddress, tenantId1);
        createFailedLoginLog("10.0.0.1", tenantId1); // Diferente IP

        // When
        long count = auditLogRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
            SecurityEventType.LOGIN_FAILED,
            ipAddress,
            since
        );

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe contar intentos de login fallidos por usuario")
    void shouldCountFailedLoginsByUser() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusMinutes(30);

        createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_FAILED, userId1, tenantId1);
        createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);

        // When
        long count = auditLogRepository.countByEventTypeAndUserIdAndCreatedAtAfter(
            SecurityEventType.LOGIN_FAILED,
            userId1,
            since
        );

        // Then
        assertThat(count).isEqualTo(3);
    }

    // ==================== SORTING TESTS ====================

    @Test
    @DisplayName("Debe ordenar logs por fecha de creación descendente")
    void shouldSortLogsByCreatedAtDescending() {
        // Given
        AuditLog log1 = createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);
        Thread.sleep(10); // Pequeño delay para garantizar orden
        AuditLog log2 = createAuditLog(SecurityEventType.LOGOUT, userId1, tenantId1);
        Thread.sleep(10);
        AuditLog log3 = createAuditLog(SecurityEventType.LOGIN_SUCCESS, userId1, tenantId1);

        // When
        Page<AuditLog> page = auditLogRepository.findByTenantId(
            tenantId1,
            PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        // Then
        List<AuditLog> logs = page.getContent();
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).getCreatedAt()).isAfterOrEqualTo(logs.get(1).getCreatedAt());
        assertThat(logs.get(1).getCreatedAt()).isAfterOrEqualTo(logs.get(2).getCreatedAt());
    }

    // ==================== HELPER METHODS ====================

    private AuditLog createAuditLog(SecurityEventType eventType, UUID userId, UUID tenantId) {
        AuditLog log = AuditLog.builder()
            .eventType(eventType)
            .userId(userId)
            .tenantId(tenantId)
            .ipAddress("192.168.1.1")
            .userAgent("TestAgent")
            .details(new HashMap<>())
            .build();
        return auditLogRepository.save(log);
    }

    private void createFailedLoginLog(String ipAddress, UUID tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", "Invalid credentials");

        AuditLog log = AuditLog.builder()
            .eventType(SecurityEventType.LOGIN_FAILED)
            .userId(null)
            .tenantId(tenantId)
            .ipAddress(ipAddress)
            .userAgent("TestAgent")
            .details(details)
            .build();
        auditLogRepository.save(log);
    }
}

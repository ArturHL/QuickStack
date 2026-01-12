package com.quickstack.core.audit;

import com.quickstack.core.security.JwtService;
import com.quickstack.core.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de controlador para AuditLogController.
 *
 * Cobertura:
 * - Endpoint GET /api/admin/audit-logs
 * - Autenticación y autorización (solo ADMIN)
 * - Parámetros de filtrado y paginación
 * - Respuestas HTTP correctas
 */
@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @MockBean
    private JwtService jwtService;

    private UUID tenantId;
    private UUID userId;
    private List<AuditLog> sampleLogs;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        Map<String, Object> details = new HashMap<>();
        details.put("email", "user@example.com");

        AuditLog log1 = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType(SecurityEventType.LOGIN_SUCCESS)
            .userId(userId)
            .tenantId(tenantId)
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0")
            .details(details)
            .createdAt(LocalDateTime.now())
            .build();

        AuditLog log2 = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType(SecurityEventType.LOGOUT)
            .userId(userId)
            .tenantId(tenantId)
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0")
            .details(new HashMap<>())
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .build();

        sampleLogs = List.of(log1, log2);
    }

    // ==================== AUTHENTICATION & AUTHORIZATION TESTS ====================

    @Test
    @DisplayName("Debe rechazar acceso sin autenticación")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Debe rechazar acceso para usuarios no-admin")
    void shouldRejectNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe permitir acceso para usuarios admin")
    void shouldAllowAdminUsers() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    // ==================== BASIC LISTING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe listar logs de auditoría con valores por defecto")
    void shouldListAuditLogsWithDefaults() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].eventType", is("LOGIN_SUCCESS")))
            .andExpect(jsonPath("$.content[0].ipAddress", is("192.168.1.100")))
            .andExpect(jsonPath("$.content[1].eventType", is("LOGOUT")));
    }

    // ==================== FILTERING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe filtrar por tenant ID")
    void shouldFilterByTenantId() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(
            eq(tenantId),
            any(),
            any(),
            any(),
            any(),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("tenantId", tenantId.toString())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe filtrar por user ID")
    void shouldFilterByUserId() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLogs.get(0)));
        when(auditService.getAuditLogs(
            any(),
            eq(userId),
            any(),
            any(),
            any(),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("userId", userId.toString())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe filtrar por tipo de evento")
    void shouldFilterByEventType() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLogs.get(0)));
        when(auditService.getAuditLogs(
            any(),
            any(),
            eq(SecurityEventType.LOGIN_SUCCESS),
            any(),
            any(),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("eventType", "LOGIN_SUCCESS")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].eventType", is("LOGIN_SUCCESS")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe filtrar por rango de fechas")
    void shouldFilterByDateRange() throws Exception {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusHours(1);
        LocalDateTime endDate = LocalDateTime.now();
        Page<AuditLog> page = new PageImpl<>(sampleLogs);

        when(auditService.getAuditLogs(
            any(),
            any(),
            any(),
            eq(startDate),
            eq(endDate),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe aplicar múltiples filtros simultáneamente")
    void shouldApplyMultipleFilters() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLogs.get(0)));
        when(auditService.getAuditLogs(
            eq(tenantId),
            eq(userId),
            eq(SecurityEventType.LOGIN_SUCCESS),
            any(),
            any(),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("tenantId", tenantId.toString())
                .param("userId", userId.toString())
                .param("eventType", "LOGIN_SUCCESS")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ==================== PAGINATION TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe paginar resultados correctamente")
    void shouldPaginateResults() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(
            sampleLogs,
            PageRequest.of(0, 20),
            50 // Total de 50 elementos
        );

        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("page", "0")
                .param("size", "20")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements", is(50)))
            .andExpect(jsonPath("$.totalPages", is(3)))
            .andExpect(jsonPath("$.size", is(20)))
            .andExpect(jsonPath("$.number", is(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe usar valores por defecto de paginación")
    void shouldUseDefaultPaginationValues() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe validar límite máximo de tamaño de página")
    void shouldValidateMaxPageSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("size", "1000") // Excede el límite
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    // ==================== SORTING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe ordenar por createdAt descendente por defecto")
    void shouldSortByCreatedAtDescByDefault() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(PageRequest.class)
        )).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].createdAt", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe permitir ordenamiento personalizado")
    void shouldAllowCustomSorting() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(sampleLogs);
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("sort", "eventType,asc")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    // ==================== RESPONSE FORMAT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe incluir todos los campos en la respuesta")
    void shouldIncludeAllFieldsInResponse() throws Exception {
        // Given
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLogs.get(0)));
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id", notNullValue()))
            .andExpect(jsonPath("$.content[0].eventType", is("LOGIN_SUCCESS")))
            .andExpect(jsonPath("$.content[0].userId", notNullValue()))
            .andExpect(jsonPath("$.content[0].tenantId", notNullValue()))
            .andExpect(jsonPath("$.content[0].ipAddress", is("192.168.1.100")))
            .andExpect(jsonPath("$.content[0].userAgent", is("Mozilla/5.0")))
            .andExpect(jsonPath("$.content[0].details", notNullValue()))
            .andExpect(jsonPath("$.content[0].createdAt", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe serializar detalles JSONB correctamente")
    void shouldSerializeJsonbDetailsCorrectly() throws Exception {
        // Given
        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("email", "test@example.com");
        complexDetails.put("attemptNumber", 3);
        complexDetails.put("reason", "Invalid password");

        AuditLog log = AuditLog.builder()
            .id(UUID.randomUUID())
            .eventType(SecurityEventType.LOGIN_FAILED)
            .userId(null)
            .tenantId(tenantId)
            .ipAddress("10.0.0.1")
            .userAgent("curl/7.64.1")
            .details(complexDetails)
            .createdAt(LocalDateTime.now())
            .build();

        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].details.email", is("test@example.com")))
            .andExpect(jsonPath("$.content[0].details.attemptNumber", is(3)))
            .andExpect(jsonPath("$.content[0].details.reason", is("Invalid password")));
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar tipo de evento inválido")
    void shouldHandleInvalidEventType() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("eventType", "INVALID_EVENT")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar UUID inválido")
    void shouldHandleInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                .param("userId", "invalid-uuid")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe retornar lista vacía si no hay resultados")
    void shouldReturnEmptyListWhenNoResults() throws Exception {
        // Given
        Page<AuditLog> emptyPage = new PageImpl<>(List.of());
        when(auditService.getAuditLogs(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/api/admin/audit-logs")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements", is(0)));
    }
}

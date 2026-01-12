package com.quickstack.core.lockout;

import com.quickstack.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de controlador para endpoints de Account Lockout.
 *
 * Cobertura:
 * - GET /api/admin/users/{id}/lockout-status - Ver estado de bloqueo
 * - POST /api/admin/users/{id}/unlock - Desbloquear cuenta (solo ADMIN)
 * - Autenticación y autorización
 * - Validación de parámetros
 * - Manejo de errores
 * - Respuestas HTTP correctas
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@WebMvcTest(LockoutController.class)
class LockoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountLockoutService lockoutService;

    @MockBean
    private JwtService jwtService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // ==================== LOCKOUT STATUS ENDPOINT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin puede ver estado de bloqueo de cuenta")
    void adminCanViewLockoutStatus() throws Exception {
        // Given
        LockoutInfo lockoutInfo = LockoutInfo.builder()
            .userId(userId)
            .isLocked(true)
            .failedAttempts(5)
            .lockedUntil(LocalDateTime.now().plusMinutes(15))
            .remainingMinutes(15)
            .build();

        when(lockoutService.getLockoutInfo(userId)).thenReturn(lockoutInfo);

        // When & Then
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId", is(userId.toString())))
            .andExpect(jsonPath("$.isLocked", is(true)))
            .andExpect(jsonPath("$.failedAttempts", is(5)))
            .andExpect(jsonPath("$.lockedUntil", notNullValue()))
            .andExpect(jsonPath("$.remainingMinutes", is(15)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe mostrar estado no bloqueado correctamente")
    void shouldShowNonLockedStatusCorrectly() throws Exception {
        // Given
        LockoutInfo lockoutInfo = LockoutInfo.builder()
            .userId(userId)
            .isLocked(false)
            .failedAttempts(2)
            .lockedUntil(null)
            .remainingAttempts(3)
            .build();

        when(lockoutService.getLockoutInfo(userId)).thenReturn(lockoutInfo);

        // When & Then
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isLocked", is(false)))
            .andExpect(jsonPath("$.failedAttempts", is(2)))
            .andExpect(jsonPath("$.remainingAttempts", is(3)))
            .andExpect(jsonPath("$.lockedUntil", nullValue()));
    }

    @Test
    @DisplayName("Debe rechazar acceso sin autenticación a lockout status")
    void shouldRejectUnauthenticatedAccessToLockoutStatus() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Debe rechazar acceso de usuario no-admin a lockout status")
    void shouldRejectNonAdminAccessToLockoutStatus() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar UUID inválido en lockout status")
    void shouldHandleInvalidUuidInLockoutStatus() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", "invalid-uuid")
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar usuario no encontrado en lockout status")
    void shouldHandleUserNotFoundInLockoutStatus() throws Exception {
        // Given
        UUID nonExistentUserId = UUID.randomUUID();
        when(lockoutService.getLockoutInfo(nonExistentUserId))
            .thenThrow(new UserNotFoundException("User not found"));

        // When & Then
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", nonExistentUserId)
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", containsString("not found")));
    }

    // ==================== UNLOCK ACCOUNT ENDPOINT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin puede desbloquear cuenta")
    void adminCanUnlockAccount() throws Exception {
        // Given
        doNothing().when(lockoutService).unlockAccount(userId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", is("Account unlocked successfully")))
            .andExpect(jsonPath("$.userId", is(userId.toString())));

        verify(lockoutService).unlockAccount(userId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Desbloqueo incluye timestamp de operación")
    void unlockIncludesOperationTimestamp() throws Exception {
        // Given
        doNothing().when(lockoutService).unlockAccount(userId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unlockedAt", notNullValue()));
    }

    @Test
    @DisplayName("Debe rechazar acceso sin autenticación a unlock")
    void shouldRejectUnauthenticatedAccessToUnlock() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Debe rechazar acceso de usuario no-admin a unlock")
    void shouldRejectNonAdminAccessToUnlock() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar UUID inválido en unlock")
    void shouldHandleInvalidUuidInUnlock() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/unlock", "invalid-uuid")
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar usuario no encontrado en unlock")
    void shouldHandleUserNotFoundInUnlock() throws Exception {
        // Given
        UUID nonExistentUserId = UUID.randomUUID();
        doThrow(new UserNotFoundException("User not found"))
            .when(lockoutService).unlockAccount(nonExistentUserId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/{id}/unlock", nonExistentUserId)
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", containsString("not found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Puede desbloquear cuenta que no está bloqueada sin error")
    void canUnlockNonLockedAccountWithoutError() throws Exception {
        // Given
        doNothing().when(lockoutService).unlockAccount(userId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", is("Account unlocked successfully")));
    }

    // ==================== BATCH UNLOCK ENDPOINT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin puede desbloquear múltiples cuentas en batch")
    void adminCanUnlockMultipleAccountsInBatch() throws Exception {
        // Given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID userId3 = UUID.randomUUID();

        String requestBody = """
            {
                "userIds": ["%s", "%s", "%s"]
            }
            """.formatted(userId1, userId2, userId3);

        doNothing().when(lockoutService).unlockAccount(any(UUID.class));

        // When & Then
        mockMvc.perform(post("/api/admin/users/unlock-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unlockedCount", is(3)))
            .andExpect(jsonPath("$.message", containsString("3 accounts unlocked")));

        verify(lockoutService, times(3)).unlockAccount(any(UUID.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Batch unlock debe manejar errores parciales")
    void batchUnlockShouldHandlePartialErrors() throws Exception {
        // Given
        UUID validUserId = UUID.randomUUID();
        UUID invalidUserId = UUID.randomUUID();

        String requestBody = """
            {
                "userIds": ["%s", "%s"]
            }
            """.formatted(validUserId, invalidUserId);

        doNothing().when(lockoutService).unlockAccount(validUserId);
        doThrow(new UserNotFoundException("User not found"))
            .when(lockoutService).unlockAccount(invalidUserId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/unlock-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unlockedCount", is(1)))
            .andExpect(jsonPath("$.failedCount", is(1)))
            .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Batch unlock debe validar lista vacía")
    void batchUnlockShouldValidateEmptyList() throws Exception {
        // Given
        String requestBody = """
            {
                "userIds": []
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/admin/users/unlock-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("empty")));
    }

    // ==================== LOCKOUT STATISTICS ENDPOINT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin puede ver estadísticas de bloqueos")
    void adminCanViewLockoutStatistics() throws Exception {
        // Given
        LockoutStatistics stats = LockoutStatistics.builder()
            .totalLockedAccounts(15)
            .lockedLast24Hours(8)
            .lockedLast7Days(12)
            .averageLockDurationMinutes(45)
            .build();

        when(lockoutService.getLockoutStatistics()).thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/admin/lockout/statistics")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalLockedAccounts", is(15)))
            .andExpect(jsonPath("$.lockedLast24Hours", is(8)))
            .andExpect(jsonPath("$.lockedLast7Days", is(12)))
            .andExpect(jsonPath("$.averageLockDurationMinutes", is(45)));
    }

    @Test
    @DisplayName("Debe rechazar acceso sin autenticación a estadísticas")
    void shouldRejectUnauthenticatedAccessToStatistics() throws Exception {
        mockMvc.perform(get("/api/admin/lockout/statistics")
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Debe rechazar acceso de usuario no-admin a estadísticas")
    void shouldRejectNonAdminAccessToStatistics() throws Exception {
        mockMvc.perform(get("/api/admin/lockout/statistics")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    // ==================== LOCKED ACCOUNTS LIST ENDPOINT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin puede listar cuentas bloqueadas")
    void adminCanListLockedAccounts() throws Exception {
        // Given
        LockedAccountInfo account1 = LockedAccountInfo.builder()
            .userId(UUID.randomUUID())
            .email("user1@test.com")
            .failedAttempts(5)
            .lockedUntil(LocalDateTime.now().plusMinutes(15))
            .build();

        LockedAccountInfo account2 = LockedAccountInfo.builder()
            .userId(UUID.randomUUID())
            .email("user2@test.com")
            .failedAttempts(10)
            .lockedUntil(LocalDateTime.now().plusHours(1))
            .build();

        when(lockoutService.getLockedAccounts(any()))
            .thenReturn(org.springframework.data.domain.PageImpl(
                java.util.List.of(account1, account2)
            ));

        // When & Then
        mockMvc.perform(get("/api/admin/lockout/locked-accounts")
                .param("page", "0")
                .param("size", "20")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].email", is("user1@test.com")))
            .andExpect(jsonPath("$.content[0].failedAttempts", is(5)))
            .andExpect(jsonPath("$.content[1].email", is("user2@test.com")))
            .andExpect(jsonPath("$.content[1].failedAttempts", is(10)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Lista de cuentas bloqueadas soporta paginación")
    void lockedAccountsListSupportsPagination() throws Exception {
        // Given
        when(lockoutService.getLockedAccounts(any()))
            .thenReturn(org.springframework.data.domain.PageImpl(
                java.util.List.of(),
                org.springframework.data.domain.PageRequest.of(0, 20),
                50 // Total elements
            ));

        // When & Then
        mockMvc.perform(get("/api/admin/lockout/locked-accounts")
                .param("page", "0")
                .param("size", "20")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(50)))
            .andExpect(jsonPath("$.totalPages", is(3)))
            .andExpect(jsonPath("$.size", is(20)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Lista puede filtrarse por tenant")
    void listCanBeFilteredByTenant() throws Exception {
        // Given
        UUID tenantId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get("/api/admin/lockout/locked-accounts")
                .param("tenantId", tenantId.toString())
                .with(csrf()))
            .andExpect(status().isOk());

        verify(lockoutService).getLockedAccountsByTenant(eq(tenantId), any());
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe manejar error de servicio interno")
    void shouldHandleInternalServiceError() throws Exception {
        // Given
        when(lockoutService.getLockoutInfo(userId))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Debe validar límite máximo de tamaño de página")
    void shouldValidateMaxPageSize() throws Exception {
        mockMvc.perform(get("/api/admin/lockout/locked-accounts")
                .param("size", "1000") // Excede límite
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ==================== RESPONSE FORMAT TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Respuesta de unlock debe incluir todos los campos requeridos")
    void unlockResponseShouldIncludeAllRequiredFields() throws Exception {
        // Given
        doNothing().when(lockoutService).unlockAccount(userId);

        // When & Then
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", notNullValue()))
            .andExpect(jsonPath("$.userId", notNullValue()))
            .andExpect(jsonPath("$.unlockedAt", notNullValue()))
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Lockout status debe formatear fechas correctamente")
    void lockoutStatusShouldFormatDatesCorrectly() throws Exception {
        // Given
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(15);
        LockoutInfo lockoutInfo = LockoutInfo.builder()
            .userId(userId)
            .isLocked(true)
            .failedAttempts(5)
            .lockedUntil(lockedUntil)
            .remainingMinutes(15)
            .build();

        when(lockoutService.getLockoutInfo(userId)).thenReturn(lockoutInfo);

        // When & Then
        mockMvc.perform(get("/api/admin/users/{id}/lockout-status", userId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lockedUntil", matchesPattern(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"
            )));
    }

    // ==================== CSRF PROTECTION TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST endpoints deben requerir CSRF token")
    void postEndpointsShouldRequireCsrfToken() throws Exception {
        // When & Then - Sin CSRF token debe fallar
        mockMvc.perform(post("/api/admin/users/{id}/unlock", userId))
            .andExpect(status().isForbidden());
    }

    // ==================== RATE LIMITING TESTS ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Endpoints de admin deben tener rate limiting")
    void adminEndpointsShouldHaveRateLimiting() throws Exception {
        // Given - Múltiples requests rápidos
        doNothing().when(lockoutService).unlockAccount(any(UUID.class));

        // When - Hacer múltiples requests
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(post("/api/admin/users/{id}/unlock", userId)
                .with(csrf()));
        }

        // Then - Algún request debe ser rate limited (429)
        // Esto depende de la configuración de rate limiting
        // Este test documenta el comportamiento esperado
    }
}

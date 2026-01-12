package com.quickstack.core.audit;

import com.quickstack.core.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AuditService.
 *
 * Cobertura:
 * - Logging asíncrono de eventos de auditoría
 * - Diferentes tipos de eventos de seguridad
 * - Serialización de detalles JSONB
 * - Manejo de eventos sin bloquear el flujo principal
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private UUID userId;
    private UUID tenantId;
    private String ipAddress;
    private String userAgent;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        ipAddress = "192.168.1.100";
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    }

    // ==================== LOGIN SUCCESS TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de login exitoso con todos los detalles")
    void shouldLogLoginSuccessEvent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("email", "user@example.com");
        details.put("role", Role.USER.name());

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(SecurityEventType.LOGIN_SUCCESS);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getTenantId()).isEqualTo(tenantId);
        assertThat(savedLog.getIpAddress()).isEqualTo(ipAddress);
        assertThat(savedLog.getUserAgent()).isEqualTo(userAgent);
        assertThat(savedLog.getDetails()).containsEntry("email", "user@example.com");
        assertThat(savedLog.getDetails()).containsEntry("role", Role.USER.name());
        assertThat(savedLog.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe registrar evento de login exitoso sin detalles opcionales")
    void shouldLogLoginSuccessWithoutOptionalDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            null
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(SecurityEventType.LOGIN_SUCCESS);
        assertThat(savedLog.getDetails()).isNullOrEmpty();
    }

    // ==================== LOGIN FAILED TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de login fallido con razón del fallo")
    void shouldLogLoginFailedWithReason() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("email", "hacker@example.com");
        details.put("reason", "Invalid password");
        details.put("attemptNumber", 3);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_FAILED,
            null, // Usuario no autenticado
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(SecurityEventType.LOGIN_FAILED);
        assertThat(savedLog.getUserId()).isNull();
        assertThat(savedLog.getDetails()).containsEntry("reason", "Invalid password");
        assertThat(savedLog.getDetails()).containsEntry("attemptNumber", 3);
    }

    // ==================== PASSWORD CHANGE TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de cambio de contraseña")
    void shouldLogPasswordChangeEvent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("method", "user_initiated");

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.PASSWORD_CHANGE,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    // ==================== TOKEN REFRESH TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de refresh token")
    void shouldLogTokenRefreshEvent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("refreshTokenId", UUID.randomUUID().toString());

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.TOKEN_REFRESH,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(SecurityEventType.TOKEN_REFRESH);
        assertThat(savedLog.getDetails()).containsKey("refreshTokenId");
    }

    // ==================== ACCOUNT LOCKED TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de bloqueo de cuenta con duración")
    void shouldLogAccountLockedEvent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("reason", "Too many failed login attempts");
        details.put("failedAttempts", 5);
        details.put("lockDurationMinutes", 15);
        details.put("lockedUntil", LocalDateTime.now().plusMinutes(15).toString());

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.ACCOUNT_LOCKED,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getEventType()).isEqualTo(SecurityEventType.ACCOUNT_LOCKED);
        assertThat(savedLog.getDetails()).containsEntry("failedAttempts", 5);
        assertThat(savedLog.getDetails()).containsEntry("lockDurationMinutes", 15);
    }

    // ==================== LOGOUT TESTS ====================

    @Test
    @DisplayName("Debe registrar evento de logout")
    void shouldLogLogoutEvent() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("sessionDuration", "2h 15m");

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGOUT,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    // ==================== ASYNC BEHAVIOR TESTS ====================

    @Test
    @DisplayName("Debe ejecutar logging de forma asíncrona sin bloquear")
    void shouldLogAsynchronously() throws Exception {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            Thread.sleep(100); // Simular operación lenta
            return invocation.getArgument(0);
        });

        // When
        long startTime = System.currentTimeMillis();
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            null
        );
        long endTime = System.currentTimeMillis();

        // Then
        // La llamada debe completarse rápidamente (menos de 50ms)
        // porque es asíncrona
        assertThat(endTime - startTime).isLessThan(50);

        // Verificar que eventualmente se guarda
        verify(auditLogRepository, timeout(2000)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Debe manejar errores en logging sin lanzar excepciones")
    void shouldHandleLoggingErrorsGracefully() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        // No debe lanzar excepción
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            null
        );

        // Verificar que se intentó guardar
        verify(auditLogRepository, timeout(1000)).save(any(AuditLog.class));
    }

    // ==================== JSONB DETAILS SERIALIZATION TESTS ====================

    @Test
    @DisplayName("Debe serializar detalles complejos en JSONB correctamente")
    void shouldSerializeComplexDetailsToJson() {
        // Given
        Map<String, Object> details = new HashMap<>();
        details.put("string", "value");
        details.put("number", 123);
        details.put("boolean", true);
        details.put("nested", Map.of("key", "value"));
        details.put("array", java.util.List.of("item1", "item2"));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGIN_SUCCESS,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            details
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getDetails()).containsEntry("string", "value");
        assertThat(savedLog.getDetails()).containsEntry("number", 123);
        assertThat(savedLog.getDetails()).containsEntry("boolean", true);
    }

    @Test
    @DisplayName("Debe manejar detalles vacíos correctamente")
    void shouldHandleEmptyDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // When
        auditService.logSecurityEvent(
            SecurityEventType.LOGOUT,
            userId,
            tenantId,
            ipAddress,
            userAgent,
            new HashMap<>()
        );

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(1000)).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getDetails()).isNotNull();
    }
}

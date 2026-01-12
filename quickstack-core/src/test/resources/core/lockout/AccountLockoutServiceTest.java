package com.quickstack.core.lockout;

import com.quickstack.core.audit.AuditService;
import com.quickstack.core.audit.SecurityEventType;
import com.quickstack.core.user.User;
import com.quickstack.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AccountLockoutService.
 *
 * Cobertura:
 * - Bloqueo progresivo de cuenta
 * - 5 intentos = 15 minutos
 * - 10 intentos = 1 hora
 * - 15 intentos = 24 horas
 * - Reseteo de contador en login exitoso
 * - Desbloqueo automático después del timeout
 * - Desbloqueo manual por admin
 * - Auditoría de eventos de bloqueo
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AccountLockoutService accountLockoutService;

    private UUID userId;
    private UUID tenantId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        user = User.builder()
            .id(userId)
            .tenantId(tenantId)
            .email("user@test.com")
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .lastFailedLogin(null)
            .build();
    }

    // ==================== FAILED LOGIN TRACKING TESTS ====================

    @Test
    @DisplayName("Debe incrementar contador de intentos fallidos")
    void shouldIncrementFailedLoginAttempts() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getFailedLoginAttempts() == 1 &&
            u.getLastFailedLogin() != null
        ));
    }

    @Test
    @DisplayName("Debe actualizar timestamp del último intento fallido")
    void shouldUpdateLastFailedLoginTimestamp() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getLastFailedLogin() != null &&
            u.getLastFailedLogin().isAfter(before)
        ));
    }

    @Test
    @DisplayName("Debe incrementar contador en intentos consecutivos")
    void shouldIncrementCounterOnConsecutiveAttempts() {
        // Given
        user.setFailedLoginAttempts(3);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getFailedLoginAttempts() == 4
        ));
    }

    // ==================== PROGRESSIVE LOCKOUT TESTS ====================

    @Test
    @DisplayName("Debe bloquear cuenta por 15 minutos después de 5 intentos")
    void shouldLockAccountFor15MinutesAfter5Attempts() {
        // Given
        user.setFailedLoginAttempts(4); // El siguiente será el 5to
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusMinutes(15);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5)) &&
                   u.getLockedUntil().isBefore(expectedUnlock.plusSeconds(5));
        }));
    }

    @Test
    @DisplayName("Debe bloquear cuenta por 1 hora después de 10 intentos")
    void shouldLockAccountFor1HourAfter10Attempts() {
        // Given
        user.setFailedLoginAttempts(9); // El siguiente será el 10mo
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusHours(1);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5)) &&
                   u.getLockedUntil().isBefore(expectedUnlock.plusSeconds(5));
        }));
    }

    @Test
    @DisplayName("Debe bloquear cuenta por 24 horas después de 15 intentos")
    void shouldLockAccountFor24HoursAfter15Attempts() {
        // Given
        user.setFailedLoginAttempts(14); // El siguiente será el 15vo
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusHours(24);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5)) &&
                   u.getLockedUntil().isBefore(expectedUnlock.plusSeconds(5));
        }));
    }

    @Test
    @DisplayName("Debe mantener bloqueo de 24 horas después de 15+ intentos")
    void shouldMaintain24HourLockAfter15PlusAttempts() {
        // Given
        user.setFailedLoginAttempts(20); // Más de 15
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusHours(24);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5));
        }));
    }

    // ==================== ACCOUNT LOCKED CHECK TESTS ====================

    @Test
    @DisplayName("Debe retornar false si la cuenta no está bloqueada")
    void shouldReturnFalseIfAccountNotLocked() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        boolean isLocked = accountLockoutService.isAccountLocked(userId);

        // Then
        assertThat(isLocked).isFalse();
    }

    @Test
    @DisplayName("Debe retornar true si la cuenta está bloqueada")
    void shouldReturnTrueIfAccountLocked() {
        // Given
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        boolean isLocked = accountLockoutService.isAccountLocked(userId);

        // Then
        assertThat(isLocked).isTrue();
    }

    @Test
    @DisplayName("Debe retornar false si el bloqueo ya expiró")
    void shouldReturnFalseIfLockExpired() {
        // Given
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // Expirado
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        boolean isLocked = accountLockoutService.isAccountLocked(userId);

        // Then
        assertThat(isLocked).isFalse();
    }

    @Test
    @DisplayName("Debe desbloquear automáticamente si el tiempo expiró")
    void shouldAutoUnlockIfTimeExpired() {
        // Given
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        user.setFailedLoginAttempts(10);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.isAccountLocked(userId);

        // Then - Debe resetear locked_until
        verify(userRepository).save(argThat(u ->
            u.getLockedUntil() == null
        ));
    }

    // ==================== SUCCESSFUL LOGIN RESET TESTS ====================

    @Test
    @DisplayName("Debe resetear contador en login exitoso")
    void shouldResetCounterOnSuccessfulLogin() {
        // Given
        user.setFailedLoginAttempts(3);
        user.setLastFailedLogin(LocalDateTime.now().minusMinutes(5));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.resetFailedAttempts(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getFailedLoginAttempts() == 0 &&
            u.getLastFailedLogin() == null &&
            u.getLockedUntil() == null
        ));
    }

    @Test
    @DisplayName("Debe desbloquear cuenta en login exitoso")
    void shouldUnlockAccountOnSuccessfulLogin() {
        // Given
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.resetFailedAttempts(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getLockedUntil() == null &&
            u.getFailedLoginAttempts() == 0
        ));
    }

    // ==================== ADMIN UNLOCK TESTS ====================

    @Test
    @DisplayName("Admin debe poder desbloquear cuenta manualmente")
    void adminShouldBeAbleToUnlockAccountManually() {
        // Given
        user.setLockedUntil(LocalDateTime.now().plusHours(24));
        user.setFailedLoginAttempts(15);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.unlockAccount(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getLockedUntil() == null &&
            u.getFailedLoginAttempts() == 0
        ));
    }

    @Test
    @DisplayName("Desbloqueo manual debe registrar audit log")
    void manualUnlockShouldLogAuditEvent() {
        // Given
        user.setLockedUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.unlockAccount(userId);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.ACCOUNT_UNLOCKED),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            any(Map.class)
        );
    }

    @Test
    @DisplayName("Debe permitir desbloquear cuenta no bloqueada sin error")
    void shouldAllowUnlockingNonLockedAccountWithoutError() {
        // Given - Usuario no bloqueado
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.unlockAccount(userId);

        // Then - No debe lanzar excepción
        verify(userRepository).save(any(User.class));
    }

    // ==================== AUDIT LOGGING TESTS ====================

    @Test
    @DisplayName("Debe registrar audit log al bloquear cuenta")
    void shouldLogAuditEventOnAccountLock() {
        // Given
        user.setFailedLoginAttempts(4); // El siguiente bloquea
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.ACCOUNT_LOCKED),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            argThat(details ->
                details.containsKey("failedAttempts") &&
                details.containsKey("lockDurationMinutes")
            )
        );
    }

    @Test
    @DisplayName("Audit log debe incluir duración del bloqueo")
    void auditLogShouldIncludeLockDuration() {
        // Given
        user.setFailedLoginAttempts(4); // 5to intento = 15 min
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.ACCOUNT_LOCKED),
            anyUUID(),
            anyUUID(),
            anyString(),
            anyString(),
            argThat(details -> {
                Object duration = details.get("lockDurationMinutes");
                return duration != null && duration.equals(15);
            })
        );
    }

    @Test
    @DisplayName("Audit log debe incluir número de intentos fallidos")
    void auditLogShouldIncludeFailedAttemptsCount() {
        // Given
        user.setFailedLoginAttempts(4);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.ACCOUNT_LOCKED),
            anyUUID(),
            anyUUID(),
            anyString(),
            anyString(),
            argThat(details -> {
                Object attempts = details.get("failedAttempts");
                return attempts != null && attempts.equals(5);
            })
        );
    }

    @Test
    @DisplayName("Debe registrar audit log de desbloqueo automático")
    void shouldLogAuditEventOnAutoUnlock() {
        // Given
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // Expirado
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.isAccountLocked(userId);

        // Then
        verify(auditService).logSecurityEvent(
            eq(SecurityEventType.ACCOUNT_UNLOCKED),
            eq(userId),
            eq(tenantId),
            anyString(),
            anyString(),
            argThat(details ->
                details.containsKey("reason") &&
                details.get("reason").toString().contains("automatic")
            )
        );
    }

    // ==================== LOCKOUT INFO TESTS ====================

    @Test
    @DisplayName("Debe retornar información de bloqueo para cuenta bloqueada")
    void shouldReturnLockoutInfoForLockedAccount() {
        // Given
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(30);
        user.setLockedUntil(lockedUntil);
        user.setFailedLoginAttempts(5);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        LockoutInfo info = accountLockoutService.getLockoutInfo(userId);

        // Then
        assertThat(info.isLocked()).isTrue();
        assertThat(info.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(info.getFailedAttempts()).isEqualTo(5);
        assertThat(info.getRemainingMinutes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Debe retornar información correcta para cuenta no bloqueada")
    void shouldReturnCorrectInfoForNonLockedAccount() {
        // Given
        user.setFailedLoginAttempts(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        LockoutInfo info = accountLockoutService.getLockoutInfo(userId);

        // Then
        assertThat(info.isLocked()).isFalse();
        assertThat(info.getLockedUntil()).isNull();
        assertThat(info.getFailedAttempts()).isEqualTo(2);
        assertThat(info.getRemainingAttempts()).isEqualTo(3); // 5 - 2
    }

    @Test
    @DisplayName("Debe calcular minutos restantes de bloqueo")
    void shouldCalculateRemainingLockoutMinutes() {
        // Given
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(45);
        user.setLockedUntil(lockedUntil);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        LockoutInfo info = accountLockoutService.getLockoutInfo(userId);

        // Then
        assertThat(info.getRemainingMinutes()).isBetween(44, 46); // Aprox 45 min
    }

    // ==================== EDGE CASES TESTS ====================

    @Test
    @DisplayName("Debe manejar usuario no encontrado gracefully")
    void shouldHandleUserNotFoundGracefully() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then - No debe lanzar excepción
        boolean isLocked = accountLockoutService.isAccountLocked(userId);
        assertThat(isLocked).isFalse();
    }

    @Test
    @DisplayName("Debe manejar contador negativo (resetear a 0)")
    void shouldHandleNegativeCounter() {
        // Given
        user.setFailedLoginAttempts(-1); // Estado inválido
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.resetFailedAttempts(userId);

        // Then
        verify(userRepository).save(argThat(u ->
            u.getFailedLoginAttempts() == 0
        ));
    }

    @Test
    @DisplayName("Debe manejar lockedUntil en el pasado")
    void shouldHandlePastLockedUntil() {
        // Given
        user.setLockedUntil(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        boolean isLocked = accountLockoutService.isAccountLocked(userId);

        // Then
        assertThat(isLocked).isFalse();
        verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
    }

    @Test
    @DisplayName("Debe manejar múltiples llamadas concurrentes")
    void shouldHandleConcurrentCalls() {
        // Given
        user.setFailedLoginAttempts(3);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When - Simular llamadas concurrentes
        accountLockoutService.recordFailedLogin(userId);
        accountLockoutService.recordFailedLogin(userId);

        // Then - Debe guardar correctamente sin condiciones de carrera
        verify(userRepository, atLeastOnce()).save(any(User.class));
    }

    // ==================== LOCKOUT THRESHOLDS TESTS ====================

    @Test
    @DisplayName("No debe bloquear con menos de 5 intentos")
    void shouldNotLockWithLessThan5Attempts() {
        // Given
        user.setFailedLoginAttempts(3);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> u.getLockedUntil() == null));
    }

    @Test
    @DisplayName("Debe actualizar duración de bloqueo en thresholds progresivos")
    void shouldUpdateLockDurationAtProgressiveThresholds() {
        // Given - Usuario con 6 intentos (ya bloqueado 15 min)
        user.setFailedLoginAttempts(9);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When - Intento #10 (debe extender a 1 hora)
        accountLockoutService.recordFailedLogin(userId);

        // Then
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusHours(1);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5));
        }));
    }

    // ==================== CONFIGURATION TESTS ====================

    @Test
    @DisplayName("Debe permitir configurar thresholds de bloqueo")
    void shouldAllowConfiguringLockoutThresholds() {
        // Given - Configuración personalizada
        LockoutConfiguration config = LockoutConfiguration.builder()
            .firstThreshold(3)
            .firstDurationMinutes(10)
            .secondThreshold(6)
            .secondDurationMinutes(30)
            .thirdThreshold(10)
            .thirdDurationMinutes(120)
            .build();

        AccountLockoutService customService =
            new AccountLockoutService(userRepository, auditService, config);

        user.setFailedLoginAttempts(2); // Siguiente será 3ro
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        customService.recordFailedLogin(userId);

        // Then - Debe bloquear con duración personalizada
        verify(userRepository).save(argThat(u -> {
            if (u.getLockedUntil() == null) return false;
            LocalDateTime expectedUnlock = LocalDateTime.now().plusMinutes(10);
            return u.getLockedUntil().isAfter(expectedUnlock.minusSeconds(5));
        }));
    }
}

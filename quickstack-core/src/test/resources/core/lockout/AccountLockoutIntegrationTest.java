package com.quickstack.core.lockout;

import com.quickstack.core.audit.AuditLogRepository;
import com.quickstack.core.audit.SecurityEventType;
import com.quickstack.core.auth.AuthService;
import com.quickstack.core.auth.InvalidCredentialsException;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.tenant.TenantRepository;
import com.quickstack.core.user.User;
import com.quickstack.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Tests de integración end-to-end para Account Lockout.
 *
 * Cobertura:
 * - Flujo completo de bloqueo de cuenta
 * - Integración con AuthService
 * - Bloqueo progresivo en intentos reales de login
 * - Desbloqueo automático por timeout
 * - Desbloqueo manual por admin
 * - Reseteo de contador en login exitoso
 * - Auditoría de eventos de bloqueo
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@Transactional
class AccountLockoutIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountLockoutService lockoutService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private String email;
    private String correctPassword;
    private String tenantSlug;
    private UUID userId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        email = "user@test.com";
        correctPassword = "correct-password";
        tenantSlug = "testcorp";

        // Registrar usuario para pruebas
        RegisterRequest registerRequest = RegisterRequest.builder()
            .tenantName("Test Corp")
            .tenantSlug(tenantSlug)
            .email(email)
            .password(correctPassword)
            .userName("Test User")
            .build();

        var response = authService.register(registerRequest);
        userId = response.getUserId();
    }

    // ==================== PROGRESSIVE LOCKOUT INTEGRATION TESTS ====================

    @Test
    @DisplayName("E2E: Cuenta se bloquea después de 5 intentos fallidos")
    void accountLocksAfter5FailedAttempts() {
        // When - 5 intentos fallidos
        for (int i = 0; i < 5; i++) {
            try {
                authService.login(createLoginRequest("wrong-password"));
            } catch (InvalidCredentialsException e) {
                // Esperado
            }
        }

        // Then - Cuenta debe estar bloqueada
        assertThat(lockoutService.isAccountLocked(userId)).isTrue();

        // El 6to intento debe fallar por cuenta bloqueada
        assertThatThrownBy(() -> authService.login(createLoginRequest(correctPassword)))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("E2E: Bloqueo progresivo: 5 intentos = 15 minutos")
    void progressiveLockout5Attempts15Minutes() {
        // When - 5 intentos fallidos
        failLogin(5);

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(16));
    }

    @Test
    @DisplayName("E2E: Bloqueo progresivo: 10 intentos = 1 hora")
    void progressiveLockout10Attempts1Hour() {
        // When - 10 intentos fallidos
        failLogin(10);

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(59));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(61));
    }

    @Test
    @DisplayName("E2E: Bloqueo progresivo: 15 intentos = 24 horas")
    void progressiveLockout15Attempts24Hours() {
        // When - 15 intentos fallidos
        failLogin(15);

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusHours(25));
    }

    // ==================== LOCKED ACCOUNT BEHAVIOR TESTS ====================

    @Test
    @DisplayName("E2E: Cuenta bloqueada no puede iniciar sesión")
    void lockedAccountCannotLogin() {
        // Given - Bloquear cuenta
        failLogin(5);

        // When & Then - Intentar login con contraseña correcta
        assertThatThrownBy(() -> authService.login(createLoginRequest(correctPassword)))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("temporarily locked");

        // Verificar que no se incrementa el contador mientras está bloqueada
        User user = userRepository.findById(userId).orElseThrow();
        int attemptsBefore = user.getFailedLoginAttempts();

        try {
            authService.login(createLoginRequest("wrong-password"));
        } catch (AccountLockedException e) {
            // Esperado
        }

        User userAfter = userRepository.findById(userId).orElseThrow();
        assertThat(userAfter.getFailedLoginAttempts()).isEqualTo(attemptsBefore);
    }

    @Test
    @DisplayName("E2E: Mensaje de error incluye tiempo de desbloqueo")
    void errorMessageIncludesUnlockTime() {
        // Given
        failLogin(5);

        // When & Then
        assertThatThrownBy(() -> authService.login(createLoginRequest(correctPassword)))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("minutes");
    }

    // ==================== AUTOMATIC UNLOCK TESTS ====================

    @Test
    @DisplayName("E2E: Cuenta se desbloquea automáticamente después del timeout")
    void accountAutoUnlocksAfterTimeout() {
        // Given - Bloquear cuenta
        failLogin(5);
        User user = userRepository.findById(userId).orElseThrow();

        // Simular que pasó el tiempo de bloqueo
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        // When - Intentar login
        var response = authService.login(createLoginRequest(correctPassword));

        // Then - Debe permitir login
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(lockoutService.isAccountLocked(userId)).isFalse();
    }

    @Test
    @DisplayName("E2E: Desbloqueo automático registra audit log")
    void autoUnlockLogsAuditEvent() {
        // Given
        failLogin(5);
        User user = userRepository.findById(userId).orElseThrow();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        auditLogRepository.deleteAll();

        // When
        lockoutService.isAccountLocked(userId);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long unlockEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.ACCOUNT_UNLOCKED)
                .count();
            assertThat(unlockEvents).isGreaterThan(0);
        });
    }

    // ==================== SUCCESSFUL LOGIN RESET TESTS ====================

    @Test
    @DisplayName("E2E: Login exitoso resetea contador de intentos fallidos")
    void successfulLoginResetsFailedAttempts() {
        // Given - 3 intentos fallidos
        failLogin(3);

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);

        // When - Login exitoso
        authService.login(createLoginRequest(correctPassword));

        // Then - Contador debe resetearse
        User userAfter = userRepository.findById(userId).orElseThrow();
        assertThat(userAfter.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(userAfter.getLastFailedLogin()).isNull();
    }

    @Test
    @DisplayName("E2E: Login exitoso después de desbloqueo limpia estado")
    void successfulLoginAfterUnlockClearsState() {
        // Given - Cuenta bloqueada y desbloqueada automáticamente
        failLogin(5);
        User user = userRepository.findById(userId).orElseThrow();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        // When - Login exitoso
        authService.login(createLoginRequest(correctPassword));

        // Then
        User userAfter = userRepository.findById(userId).orElseThrow();
        assertThat(userAfter.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(userAfter.getLockedUntil()).isNull();
        assertThat(userAfter.getLastFailedLogin()).isNull();
    }

    // ==================== ADMIN UNLOCK TESTS ====================

    @Test
    @DisplayName("E2E: Admin puede desbloquear cuenta manualmente")
    void adminCanUnlockAccountManually() {
        // Given - Cuenta bloqueada
        failLogin(5);
        assertThat(lockoutService.isAccountLocked(userId)).isTrue();

        // When - Admin desbloquea
        lockoutService.unlockAccount(userId);

        // Then
        assertThat(lockoutService.isAccountLocked(userId)).isFalse();

        // Usuario debe poder hacer login inmediatamente
        var response = authService.login(createLoginRequest(correctPassword));
        assertThat(response.getAccessToken()).isNotNull();
    }

    @Test
    @DisplayName("E2E: Desbloqueo manual resetea contador de intentos")
    void manualUnlockResetsFailedAttempts() {
        // Given
        failLogin(10);

        // When
        lockoutService.unlockAccount(userId);

        // Then
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastFailedLogin()).isNull();
    }

    @Test
    @DisplayName("E2E: Desbloqueo manual registra audit log")
    void manualUnlockLogsAuditEvent() {
        // Given
        failLogin(5);
        auditLogRepository.deleteAll();

        // When
        lockoutService.unlockAccount(userId);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long unlockEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.ACCOUNT_UNLOCKED)
                .filter(log -> log.getDetails().containsKey("reason"))
                .filter(log -> log.getDetails().get("reason").toString().contains("manual"))
                .count();
            assertThat(unlockEvents).isGreaterThan(0);
        });
    }

    // ==================== AUDIT LOGGING TESTS ====================

    @Test
    @DisplayName("E2E: Bloqueo de cuenta registra audit log")
    void accountLockLogsAuditEvent() {
        // Given
        auditLogRepository.deleteAll();

        // When
        failLogin(5);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long lockEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.ACCOUNT_LOCKED)
                .count();
            assertThat(lockEvents).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("E2E: Audit log incluye detalles del bloqueo")
    void auditLogIncludesLockoutDetails() {
        // Given
        auditLogRepository.deleteAll();

        // When
        failLogin(5);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            var lockEvent = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.ACCOUNT_LOCKED)
                .findFirst()
                .orElseThrow();

            assertThat(lockEvent.getDetails()).containsKey("failedAttempts");
            assertThat(lockEvent.getDetails()).containsKey("lockDurationMinutes");
            assertThat(lockEvent.getDetails().get("failedAttempts")).isEqualTo(5);
            assertThat(lockEvent.getDetails().get("lockDurationMinutes")).isEqualTo(15);
        });
    }

    @Test
    @DisplayName("E2E: Múltiples bloqueos registran múltiples audit logs")
    void multipleLocksLogMultipleAuditEvents() {
        // Given
        auditLogRepository.deleteAll();

        // When - Bloquear, desbloquear, volver a bloquear
        failLogin(5);
        lockoutService.unlockAccount(userId);
        failLogin(5);

        // Then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            long lockEvents = auditLogRepository.findAll().stream()
                .filter(log -> log.getEventType() == SecurityEventType.ACCOUNT_LOCKED)
                .count();
            assertThat(lockEvents).isEqualTo(2);
        });
    }

    // ==================== LOCKOUT INFO TESTS ====================

    @Test
    @DisplayName("E2E: Puede obtener información de bloqueo de cuenta")
    void canGetLockoutInfo() {
        // Given
        failLogin(5);

        // When
        LockoutInfo info = lockoutService.getLockoutInfo(userId);

        // Then
        assertThat(info.isLocked()).isTrue();
        assertThat(info.getFailedAttempts()).isEqualTo(5);
        assertThat(info.getLockedUntil()).isNotNull();
        assertThat(info.getRemainingMinutes()).isGreaterThan(0);
        assertThat(info.getRemainingMinutes()).isLessThanOrEqualTo(15);
    }

    @Test
    @DisplayName("E2E: Info muestra intentos restantes antes de bloqueo")
    void infoShowsRemainingAttemptsBeforeLock() {
        // Given
        failLogin(2);

        // When
        LockoutInfo info = lockoutService.getLockoutInfo(userId);

        // Then
        assertThat(info.isLocked()).isFalse();
        assertThat(info.getFailedAttempts()).isEqualTo(2);
        assertThat(info.getRemainingAttempts()).isEqualTo(3); // 5 - 2
    }

    // ==================== CONCURRENT ATTEMPTS TESTS ====================

    @Test
    @DisplayName("E2E: Maneja múltiples intentos fallidos concurrentes")
    void handlesMultipleConcurrentFailedAttempts() throws Exception {
        // Given
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When - Intentos concurrentes
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    authService.login(createLoginRequest("wrong-password"));
                } catch (Exception e) {
                    // Esperado
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Debe estar bloqueada y el contador debe ser consistente
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getFailedLoginAttempts()).isGreaterThanOrEqualTo(5);
            assertThat(user.getLockedUntil()).isNotNull();
        });
    }

    // ==================== MULTI-TENANT ISOLATION TESTS ====================

    @Test
    @DisplayName("E2E: Bloqueos son aislados por tenant")
    void lockoutsAreIsolatedByTenant() {
        // Given - Crear usuario en otro tenant
        RegisterRequest otherTenant = RegisterRequest.builder()
            .tenantName("Other Corp")
            .tenantSlug("othercorp")
            .email("user@other.com")
            .password("password123")
            .userName("Other User")
            .build();

        var otherUser = authService.register(otherTenant);

        // When - Bloquear usuario del primer tenant
        failLogin(5);

        // Then - Usuario del otro tenant NO debe estar bloqueado
        assertThat(lockoutService.isAccountLocked(userId)).isTrue();
        assertThat(lockoutService.isAccountLocked(otherUser.getUserId())).isFalse();

        // Usuario del otro tenant puede hacer login normalmente
        LoginRequest otherLogin = LoginRequest.builder()
            .email("user@other.com")
            .password("password123")
            .tenantSlug("othercorp")
            .build();

        var response = authService.login(otherLogin);
        assertThat(response.getAccessToken()).isNotNull();
    }

    // ==================== INTEGRATION WITH REFRESH TOKENS TESTS ====================

    @Test
    @DisplayName("E2E: Cuenta bloqueada no puede usar refresh tokens")
    void lockedAccountCannotUseRefreshTokens() {
        // Given - Login exitoso y obtener refresh token
        var loginResponse = authService.login(createLoginRequest(correctPassword));
        String refreshToken = loginResponse.getRefreshToken();

        // Bloquear cuenta
        failLogin(5);

        // When & Then - Intentar usar refresh token debe fallar
        assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
            .isInstanceOf(AccountLockedException.class);
    }

    // ==================== HELPER METHODS ====================

    private LoginRequest createLoginRequest(String password) {
        return LoginRequest.builder()
            .email(email)
            .password(password)
            .tenantSlug(tenantSlug)
            .build();
    }

    private void failLogin(int times) {
        for (int i = 0; i < times; i++) {
            try {
                authService.login(createLoginRequest("wrong-password-" + i));
            } catch (Exception e) {
                // Esperado - puede ser InvalidCredentialsException o AccountLockedException
            }
        }
    }
}

package com.quickstack.core.user;

import com.quickstack.core.tenant.Tenant;
import com.quickstack.core.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests para UserRepository.
 * Usa @DataJpaTest que configura una DB en memoria (H2) automáticamente.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant testTenant;
    private UUID tenantId;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Primero creamos un tenant (requerido por FK)
        testTenant = Tenant.builder()
                .name("Test Company")
                .slug("test-company")
                .build();
        testTenant = tenantRepository.save(testTenant);
        tenantId = testTenant.getId();

        // Ahora podemos crear usuarios
        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashedpassword123")
                .name("Test User")
                .tenantId(tenantId)
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("Debe guardar y recuperar un usuario por ID")
    void shouldSaveAndFindUserById() {
        // When
        User saved = userRepository.save(testUser);
        Optional<User> found = userRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Debe encontrar usuario por email y tenant_id")
    void shouldFindByEmailAndTenantId() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> found = userRepository.findByEmailAndTenantId("test@example.com", tenantId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("No debe encontrar usuario con email correcto pero tenant incorrecto")
    void shouldNotFindUserWithWrongTenant() {
        // Given
        userRepository.save(testUser);
        UUID otherTenantId = UUID.randomUUID();

        // When
        Optional<User> found = userRepository.findByEmailAndTenantId("test@example.com", otherTenantId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Debe listar todos los usuarios de un tenant")
    void shouldFindAllUsersByTenantId() {
        // Given
        User user1 = User.builder()
                .email("user1@example.com")
                .passwordHash("hash1")
                .name("User One")
                .tenantId(tenantId)
                .role(Role.USER)
                .build();

        User user2 = User.builder()
                .email("user2@example.com")
                .passwordHash("hash2")
                .name("User Two")
                .tenantId(tenantId)
                .role(Role.ADMIN)
                .build();

        // Crear otro tenant para el usuario que no debe aparecer
        Tenant otherTenant = tenantRepository.save(Tenant.builder()
                .name("Other Company")
                .slug("other-company")
                .build());

        User userOtherTenant = User.builder()
                .email("other@example.com")
                .passwordHash("hash3")
                .name("Other User")
                .tenantId(otherTenant.getId())
                .role(Role.USER)
                .build();

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(userOtherTenant);

        // When
        List<User> tenantUsers = userRepository.findByTenantId(tenantId);

        // Then
        assertThat(tenantUsers).hasSize(2);
        assertThat(tenantUsers).extracting(User::getEmail)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
    }

    @Test
    @DisplayName("Debe verificar que el email existe en un tenant")
    void shouldCheckIfEmailExistsInTenant() {
        // Given
        userRepository.save(testUser);

        // When & Then
        assertThat(userRepository.existsByEmailAndTenantId("test@example.com", tenantId)).isTrue();
        assertThat(userRepository.existsByEmailAndTenantId("nonexistent@example.com", tenantId)).isFalse();
        assertThat(userRepository.existsByEmailAndTenantId("test@example.com", UUID.randomUUID())).isFalse();
    }
}

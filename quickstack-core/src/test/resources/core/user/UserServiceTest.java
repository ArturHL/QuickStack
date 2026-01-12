package com.quickstack.core.user;

import com.quickstack.core.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests para UserService.
 * Usa Mockito para mockear dependencias (UserRepository).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UUID tenantId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hashedpassword")
                .name("Test User")
                .tenantId(tenantId)
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Debe obtener usuario por ID")
    void shouldGetUserById() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = userService.getById(userId);

        // Then
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando usuario no existe")
    void shouldThrowWhenUserNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getById(nonExistentId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("Debe listar todos los usuarios de un tenant")
    void shouldGetAllUsersByTenant() {
        // Given
        User user1 = User.builder()
                .id(UUID.randomUUID())
                .email("user1@example.com")
                .name("User One")
                .tenantId(tenantId)
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        User user2 = User.builder()
                .id(UUID.randomUUID())
                .email("user2@example.com")
                .name("User Two")
                .tenantId(tenantId)
                .role(Role.ADMIN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(user1, user2));

        // When
        List<UserResponse> users = userService.getAllByTenant(tenantId);

        // Then
        assertThat(users).hasSize(2);
        assertThat(users).extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
    }

    @Test
    @DisplayName("Debe encontrar usuario por email y tenant")
    void shouldFindByEmailAndTenant() {
        // Given
        when(userRepository.findByEmailAndTenantId("test@example.com", tenantId))
                .thenReturn(Optional.of(testUser));

        // When
        Optional<UserResponse> found = userService.findByEmailAndTenant("test@example.com", tenantId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("UserResponse no debe incluir passwordHash")
    void userResponseShouldNotExposePassword() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse response = userService.getById(userId);

        // Then - verificamos que UserResponse no tiene campo passwordHash
        // Esto es una verificación de diseño: el DTO no expone datos sensibles
        assertThat(response).hasNoNullFieldsOrProperties();
        assertThat(response.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("passwordHash", "password");
    }
}

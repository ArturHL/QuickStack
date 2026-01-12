package com.quickstack.core.user;

import com.quickstack.core.security.JwtAuthenticationFilter;
import com.quickstack.core.security.ratelimit.RateLimitService;
import com.quickstack.core.user.dto.UserResponse;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para UserController.
 * Usa @WebMvcTest que solo carga la capa web (sin DB, sin servicios reales).
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        // Configurar el filtro mock para que inyecte tenantId solo si hay autenticación
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);

            // Solo inyectar tenantId si hay @WithMockUser (simula comportamiento real del filtro JWT)
            // En tests reales, el filtro JWT solo inyecta attributes cuando el token es válido
            if (request.getUserPrincipal() != null) {
                request.setAttribute("tenantId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
                request.setAttribute("userId", UUID.fromString("00000000-0000-0000-0000-000000000002"));
            }

            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        // Configurar RateLimitService para permitir todos los requests en tests
        Bucket mockBucket = org.mockito.Mockito.mock(Bucket.class);
        when(mockBucket.tryConsume(anyLong())).thenReturn(true);
        when(rateLimitService.resolveBucket(anyString(), anyInt(), anyInt(), anyLong()))
                .thenReturn(mockBucket);
    }

    private UserResponse createTestUserResponse(UUID id, String email, String name) {
        return UserResponse.builder()
                .id(id)
                .email(email)
                .name(name)
                .tenantId(UUID.randomUUID())
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/users/{id} - Debe retornar usuario por ID")
    @WithMockUser  // Simula usuario autenticado
    void shouldGetUserById() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserResponse user = createTestUserResponse(userId, "test@example.com", "Test User");
        when(userService.getUserById(userId, tenantId)).thenReturn(user);

        // When & Then
        mockMvc.perform(get("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.name", is("Test User")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Debe retornar 404 si usuario no existe")
    @WithMockUser
    void shouldReturn404WhenUserNotFound() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(userService.getUserById(userId, tenantId)).thenThrow(new UserNotFoundException(userId));

        // When & Then
        mockMvc.perform(get("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/users - Debe listar usuarios del tenant")
    @WithMockUser
    void shouldListUsersForTenant() throws Exception {
        // Given
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<UserResponse> users = List.of(
                createTestUserResponse(UUID.randomUUID(), "user1@example.com", "User One"),
                createTestUserResponse(UUID.randomUUID(), "user2@example.com", "User Two")
        );
        when(userService.getAllUsers(tenantId)).thenReturn(users);

        // When & Then (tenantId viene de @RequestAttribute inyectado por el filtro mock)
        mockMvc.perform(get("/api/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email", is("user1@example.com")))
                .andExpect(jsonPath("$[1].email", is("user2@example.com")));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Debe retornar 401 sin autenticación")
    void shouldReturn401WithoutAuthentication() throws Exception {
        // When & Then (sin @WithMockUser = no autenticado)
        // En @WebMvcTest, Spring Security devuelve 401 (Unauthorized) cuando no hay autenticación
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}

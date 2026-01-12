package com.quickstack.core.user;

import com.quickstack.core.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para operaciones de usuarios.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Obtiene un usuario por ID.
     * GET /api/users/{id}
     * El tenantId se obtiene del JWT token (inyectado por JwtAuthenticationFilter).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(
            @PathVariable UUID id,
            @RequestAttribute("tenantId") UUID tenantId
    ) {
        UserResponse user = userService.getUserById(id, tenantId);
        return ResponseEntity.ok(user);
    }

    /**
     * Lista todos los usuarios del tenant autenticado.
     * GET /api/users
     * El tenantId se obtiene del JWT token (inyectado por JwtAuthenticationFilter).
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllByTenant(
            @RequestAttribute("tenantId") UUID tenantId
    ) {
        List<UserResponse> users = userService.getAllUsers(tenantId);
        return ResponseEntity.ok(users);
    }
}

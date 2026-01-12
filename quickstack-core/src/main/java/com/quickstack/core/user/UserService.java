package com.quickstack.core.user;

import com.quickstack.core.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio con lógica de negocio para usuarios.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Obtiene un usuario por ID.
     * @throws UserNotFoundException si no existe
     */
    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserResponse.fromEntity(user);
    }

    /**
     * Obtiene un usuario por ID validando que pertenece al tenant autenticado.
     * IMPORTANTE: Este método debe usarse en endpoints protegidos para prevenir
     * acceso cross-tenant.
     *
     * @param id ID del usuario
     * @param tenantId ID del tenant del usuario autenticado (desde JWT)
     * @return UserResponse si existe y pertenece al tenant
     * @throws UserNotFoundException si no existe o pertenece a otro tenant
     */
    public UserResponse getUserById(UUID id, UUID tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // CRÍTICO: Validar que el usuario pertenece al tenant autenticado
        if (!user.getTenantId().equals(tenantId)) {
            throw new UserNotFoundException(id);
        }

        return UserResponse.fromEntity(user);
    }

    /**
     * Busca usuario por email y tenant.
     */
    public Optional<UserResponse> findByEmailAndTenant(String email, UUID tenantId) {
        return userRepository.findByEmailAndTenantId(email, tenantId)
                .map(UserResponse::fromEntity);
    }

    /**
     * Lista todos los usuarios de un tenant.
     */
    public List<UserResponse> getAllByTenant(UUID tenantId) {
        return userRepository.findByTenantId(tenantId).stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    /**
     * Alias de getAllByTenant para consistencia con getUserById.
     * Lista todos los usuarios del tenant autenticado.
     */
    public List<UserResponse> getAllUsers(UUID tenantId) {
        return getAllByTenant(tenantId);
    }
}

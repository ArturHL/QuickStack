package com.quickstack.core.user.dto;

import com.quickstack.core.user.Role;
import com.quickstack.core.user.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para respuestas de usuario.
 * NO incluye passwordHash por seguridad.
 */
@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String name;
    private UUID tenantId;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;

    /**
     * Convierte una Entity User a UserResponse.
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

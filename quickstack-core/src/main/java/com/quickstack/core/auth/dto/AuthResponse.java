package com.quickstack.core.auth.dto;

import com.quickstack.core.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response de autenticación (login/register).
 * Contiene el token JWT y datos básicos del usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private UUID userId;
    private String email;
    private String name;
    private UUID tenantId;
    private String tenantName;
    private Role role;

    public static AuthResponse of(String token, UUID userId, String email, String name,
                                   UUID tenantId, String tenantName, Role role) {
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(userId)
                .email(email)
                .name(name)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .role(role)
                .build();
    }
}

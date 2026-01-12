package com.quickstack.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un nuevo tenant con su usuario admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "El nombre del tenant es requerido")
    private String tenantName;

    @NotBlank(message = "El slug del tenant es requerido")
    @Size(min = 3, max = 50, message = "El slug debe tener entre 3 y 50 caracteres")
    private String tenantSlug;

    @NotBlank(message = "El email es requerido")
    @Email(message = "El email debe ser válido")
    private String email;

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "El nombre del usuario es requerido")
    private String userName;
}

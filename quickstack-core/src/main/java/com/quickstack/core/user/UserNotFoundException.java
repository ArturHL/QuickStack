package com.quickstack.core.user;

import java.util.UUID;

/**
 * Excepción lanzada cuando no se encuentra un usuario.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Usuario no encontrado con ID: " + id);
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}

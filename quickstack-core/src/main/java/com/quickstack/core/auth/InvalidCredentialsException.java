package com.quickstack.core.auth;

/**
 * Excepción cuando las credenciales de login son inválidas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

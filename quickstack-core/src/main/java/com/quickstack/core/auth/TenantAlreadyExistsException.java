package com.quickstack.core.auth;

/**
 * Excepción cuando se intenta registrar un tenant con slug que ya existe.
 */
public class TenantAlreadyExistsException extends RuntimeException {

    public TenantAlreadyExistsException(String slug) {
        super("Ya existe un tenant con el slug: " + slug);
    }
}

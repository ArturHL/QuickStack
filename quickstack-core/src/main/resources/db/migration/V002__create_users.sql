-- V002__create_users.sql
-- Tabla de usuarios
-- Cada usuario pertenece a un tenant

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    -- Email único por tenant (mismo email puede existir en diferentes tenants)
    CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id)
);

-- Índice para login (búsqueda por email)
CREATE INDEX idx_users_email ON users(email);

-- Índice para listar usuarios de un tenant
CREATE INDEX idx_users_tenant_id ON users(tenant_id);

-- Índice para filtrar usuarios activos
CREATE INDEX idx_users_active ON users(active);

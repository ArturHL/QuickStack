-- V001__create_tenants.sql
-- Tabla de tenants (organizaciones/clientes)
-- Cada tenant es una empresa que usa nuestra plataforma

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Índice para búsquedas por slug (usado en URLs)
CREATE INDEX idx_tenants_slug ON tenants(slug);

-- Índice para filtrar tenants activos
CREATE INDEX idx_tenants_active ON tenants(active);

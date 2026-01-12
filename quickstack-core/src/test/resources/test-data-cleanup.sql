-- Limpiar datos de tests E2E
DELETE FROM users WHERE email LIKE '%e2e%' OR email LIKE '%tenant%';
DELETE FROM tenants WHERE slug LIKE '%e2e%' OR slug LIKE '%tenant%';

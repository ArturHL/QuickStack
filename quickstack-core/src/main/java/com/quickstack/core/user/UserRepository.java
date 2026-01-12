package com.quickstack.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para operaciones de User en la base de datos.
 * Spring Data JPA genera la implementación automáticamente.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca usuario por email y tenant.
     * Usado para login (un email puede existir en múltiples tenants).
     */
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    /**
     * Lista todos los usuarios de un tenant.
     * Usado para gestión de usuarios dentro de una organización.
     */
    List<User> findByTenantId(UUID tenantId);

    /**
     * Verifica si existe un usuario con ese email en el tenant.
     * Usado para validar antes de registrar.
     */
    boolean existsByEmailAndTenantId(String email, UUID tenantId);
}

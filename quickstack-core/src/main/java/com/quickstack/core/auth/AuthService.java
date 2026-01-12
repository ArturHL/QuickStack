package com.quickstack.core.auth;

import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import com.quickstack.core.security.JwtService;
import com.quickstack.core.tenant.Tenant;
import com.quickstack.core.tenant.TenantRepository;
import com.quickstack.core.user.Role;
import com.quickstack.core.user.User;
import com.quickstack.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticación.
 * Maneja registro de tenants y login de usuarios.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Registra un nuevo tenant con su usuario administrador.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Verificar que el slug no exista
        if (tenantRepository.existsBySlug(request.getTenantSlug())) {
            throw new TenantAlreadyExistsException(request.getTenantSlug());
        }

        // Crear tenant
        Tenant tenant = Tenant.builder()
                .name(request.getTenantName())
                .slug(request.getTenantSlug())
                .build();
        tenant = tenantRepository.save(tenant);

        // Crear usuario admin
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getUserName())
                .tenantId(tenant.getId())
                .role(Role.ADMIN)
                .build();
        user = userRepository.save(user);

        // Generar token
        String token = jwtService.generateToken(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getRole()
        );

        return AuthResponse.of(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                tenant.getId(),
                tenant.getName(),
                user.getRole()
        );
    }

    /**
     * Autentica un usuario y devuelve un token JWT.
     */
    public AuthResponse login(LoginRequest request) {
        // Buscar tenant por slug
        Tenant tenant = tenantRepository.findBySlug(request.getTenantSlug())
                .orElseThrow(InvalidCredentialsException::new);

        // Buscar usuario por email y tenant
        User user = userRepository.findByEmailAndTenantId(request.getEmail(), tenant.getId())
                .orElseThrow(InvalidCredentialsException::new);

        // Verificar que el usuario esté activo
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        // Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Generar token
        String token = jwtService.generateToken(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getRole()
        );

        return AuthResponse.of(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                tenant.getId(),
                tenant.getName(),
                user.getRole()
        );
    }
}

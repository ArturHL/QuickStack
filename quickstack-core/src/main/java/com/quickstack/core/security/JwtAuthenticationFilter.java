package com.quickstack.core.security;

import com.quickstack.core.user.Role;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtro que intercepta todas las peticiones HTTP y valida el JWT.
 * Si el token es válido, establece el contexto de seguridad de Spring.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extraer token del header Authorization
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No hay token o formato incorrecto → continuar sin autenticar
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // Remover "Bearer "

        // Validar que el token no esté vacío
        if (token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. Validar token
            if (!jwtService.isTokenValid(token)) {
                // Token inválido o expirado → continuar sin autenticar
                filterChain.doFilter(request, response);
                return;
            }

            // 3. Extraer claims del token
            UUID userId = jwtService.extractUserId(token);
            UUID tenantId = jwtService.extractTenantId(token);
            String email = jwtService.extractEmail(token);
            Role role = jwtService.extractRole(token);

            // 4. Crear objeto Authentication de Spring Security
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userId,  // Principal
                    null,    // Credentials (no las necesitamos en JWT stateless)
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );

            // 5. Establecer Authentication en SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 6. Inyectar datos en request attributes (para usar en Controllers)
            request.setAttribute("userId", userId);
            request.setAttribute("tenantId", tenantId);
            request.setAttribute("userEmail", email);

        } catch (JwtException | IllegalArgumentException e) {
            // Token malformado o error al parsear → continuar sin autenticar
            // No lanzamos excepción porque Spring Security maneja el 401
        }

        // 7. Continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }
}

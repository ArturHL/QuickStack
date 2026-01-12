package com.quickstack.core.security.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor que aplica rate limiting a endpoints específicos.
 *
 * Configuraciones por endpoint:
 * - /api/auth/login: 5 intentos cada 15 minutos (prevenir brute force)
 * - /api/auth/register: 3 intentos cada 60 minutos (prevenir spam)
 * - /api/**: 100 requests por minuto (protección general API)
 *
 * El rate limiting se aplica por IP del cliente.
 * Si el request excede el límite, retorna 429 Too Many Requests.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String uri = request.getRequestURI();

        // Solo aplicar rate limiting a endpoints protegidos
        if (!shouldRateLimit(uri)) {
            return true;
        }

        String clientIp = getClientIp(request);
        RateLimitConfig config = getRateLimitConfig(uri);

        // Construir clave única: "tipo:ip"
        String key = config.prefix + ":" + clientIp;

        // Obtener bucket y consumir token
        Bucket bucket = rateLimitService.resolveBucket(
                key,
                config.capacity,
                config.refillTokens,
                config.refillMinutes
        );

        if (bucket.tryConsume(1)) {
            return true; // Request permitido
        } else {
            // Rate limit excedido
            rejectRequest(response);
            return false;
        }
    }

    /**
     * Determina si el endpoint debe tener rate limiting.
     */
    private boolean shouldRateLimit(String uri) {
        return uri.startsWith("/api/auth/login") ||
               uri.startsWith("/api/auth/register") ||
               uri.startsWith("/api/");
    }

    /**
     * Obtiene la configuración de rate limit según el endpoint.
     */
    private RateLimitConfig getRateLimitConfig(String uri) {
        if (uri.startsWith("/api/auth/login")) {
            return new RateLimitConfig("login", 5, 5, 15);
        } else if (uri.startsWith("/api/auth/register")) {
            return new RateLimitConfig("register", 3, 3, 60);
        } else {
            // Cualquier otro endpoint /api/**
            return new RateLimitConfig("api", 100, 100, 1);
        }
    }

    /**
     * Extrae la IP real del cliente.
     * Primero revisa X-Forwarded-For (para proxies/load balancers),
     * luego usa getRemoteAddr() como fallback.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For puede tener múltiples IPs: "client, proxy1, proxy2"
            // Tomar solo la primera (IP real del cliente)
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Rechaza el request con HTTP 429 Too Many Requests.
     */
    private void rejectRequest(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
        );
    }

    /**
     * Clase interna para configuración de rate limiting.
     */
    private static class RateLimitConfig {
        final String prefix;
        final int capacity;
        final int refillTokens;
        final long refillMinutes;

        RateLimitConfig(String prefix, int capacity, int refillTokens, long refillMinutes) {
            this.prefix = prefix;
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillMinutes = refillMinutes;
        }
    }
}

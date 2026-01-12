package com.quickstack.core.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de Rate Limiting usando Bucket4j.
 *
 * Implementa el algoritmo Token Bucket:
 * - Cada clave (IP, email, userId) tiene su propio bucket
 * - El bucket tiene una capacidad máxima de tokens
 * - Cada request consume 1 token
 * - Los tokens se rellenan a una tasa configurada
 *
 * Ejemplo: 5 requests cada 15 minutos
 * - Capacidad: 5 tokens
 * - Refill: 5 tokens cada 15 minutos
 * - Request consume 1 token
 * - Si no hay tokens → request rechazado (429 Too Many Requests)
 */
@Service
public class RateLimitService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Obtiene o crea un bucket para una clave específica.
     *
     * @param key            Identificador único (ej: "login:192.168.1.1", "api:user-123")
     * @param capacity       Capacidad máxima de tokens
     * @param refillTokens   Cantidad de tokens a rellenar
     * @param refillMinutes  Cada cuántos minutos se rellenan los tokens
     * @return Bucket configurado
     */
    public Bucket resolveBucket(String key, int capacity, int refillTokens, long refillMinutes) {
        return cache.computeIfAbsent(key, k -> createNewBucket(capacity, refillTokens, refillMinutes));
    }

    /**
     * Crea un nuevo bucket con la configuración especificada.
     *
     * @param capacity       Capacidad máxima
     * @param refillTokens   Tokens a rellenar
     * @param refillMinutes  Periodo de refill en minutos
     * @return Bucket nuevo
     */
    private Bucket createNewBucket(int capacity, int refillTokens, long refillMinutes) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillTokens, Duration.ofMinutes(refillMinutes))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}

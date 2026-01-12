package com.quickstack.core.security.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests para RateLimitInterceptor.
 *
 * Este interceptor aplica rate limiting a endpoints específicos:
 * - /api/auth/login: 5 intentos cada 15 minutos
 * - /api/auth/register: 3 intentos cada 60 minutos
 * - /api/**: 100 requests por minuto
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Bucket bucket;

    @Mock
    private PrintWriter writer;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(rateLimitService);
    }

    // ==================== LOGIN ENDPOINT ====================

    @Test
    @DisplayName("Debe permitir request a /api/auth/login si hay tokens disponibles")
    void shouldAllowLoginWhenTokensAvailable() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(5), eq(5), eq(15L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(rateLimitService).resolveBucket("login:192.168.1.1", 5, 5, 15);
        verify(bucket).tryConsume(1);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Debe rechazar request a /api/auth/login si no hay tokens (429)")
    void shouldRejectLoginWhenNoTokens() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(5), eq(5), eq(15L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);
        when(response.getWriter()).thenReturn(writer);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(429); // Too Many Requests
        verify(response).getWriter();
    }

    // ==================== REGISTER ENDPOINT ====================

    @Test
    @DisplayName("Debe permitir request a /api/auth/register si hay tokens disponibles")
    void shouldAllowRegisterWhenTokensAvailable() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/register");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(3), eq(3), eq(60L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(rateLimitService).resolveBucket("register:192.168.1.1", 3, 3, 60);
    }

    @Test
    @DisplayName("Debe rechazar request a /api/auth/register si no hay tokens (429)")
    void shouldRejectRegisterWhenNoTokens() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/register");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(3), eq(3), eq(60L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);
        when(response.getWriter()).thenReturn(writer);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(429);
    }

    // ==================== API ENDPOINTS ====================

    @Test
    @DisplayName("Debe permitir request a /api/users si hay tokens disponibles")
    void shouldAllowApiWhenTokensAvailable() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(100), eq(100), eq(1L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(rateLimitService).resolveBucket("api:192.168.1.1", 100, 100, 1);
    }

    @Test
    @DisplayName("Debe rechazar request a /api/users si no hay tokens (429)")
    void shouldRejectApiWhenNoTokens() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), eq(100), eq(100), eq(1L)))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);
        when(response.getWriter()).thenReturn(writer);

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(429);
    }

    // ==================== RATE LIMIT KEY CONSTRUCTION ====================

    @Test
    @DisplayName("Debe usar IP diferente para crear claves únicas por IP")
    void shouldUseDifferentIpsForUniqueKeys() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(rateLimitService.resolveBucket(anyString(), anyInt(), anyInt(), anyLong()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When - IP 1
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        interceptor.preHandle(request, response, new Object());

        // When - IP 2
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        interceptor.preHandle(request, response, new Object());

        // Then - Dos claves diferentes
        verify(rateLimitService).resolveBucket("login:192.168.1.1", 5, 5, 15);
        verify(rateLimitService).resolveBucket("login:192.168.1.2", 5, 5, 15);
    }

    // ==================== NON-RATE-LIMITED ENDPOINTS ====================

    @Test
    @DisplayName("Debe permitir requests a endpoints no protegidos sin rate limiting")
    void shouldAllowNonProtectedEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // When
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(rateLimitService, never()).resolveBucket(anyString(), anyInt(), anyInt(), anyLong());
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Debe manejar IPs con X-Forwarded-For header")
    void shouldHandleXForwardedForHeader() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(rateLimitService.resolveBucket(anyString(), anyInt(), anyInt(), anyLong()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When
        interceptor.preHandle(request, response, new Object());

        // Then - debe usar la IP real, no la del proxy
        verify(rateLimitService).resolveBucket("login:203.0.113.1", 5, 5, 15);
    }

    @Test
    @DisplayName("Debe manejar múltiples IPs en X-Forwarded-For (usar la primera)")
    void shouldUseFirstIpFromXForwardedFor() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1, 192.168.1.1");
        when(rateLimitService.resolveBucket(anyString(), anyInt(), anyInt(), anyLong()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // When
        interceptor.preHandle(request, response, new Object());

        // Then
        verify(rateLimitService).resolveBucket("login:203.0.113.1", 5, 5, 15);
    }
}

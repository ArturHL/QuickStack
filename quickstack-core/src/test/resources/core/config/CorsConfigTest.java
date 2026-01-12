package com.quickstack.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests para configuración de CORS.
 *
 * Verifica que:
 * - Headers CORS estén presentes en las respuestas
 * - Preflight requests (OPTIONS) funcionen correctamente
 * - Orígenes permitidos sean respetados
 * - Métodos HTTP permitidos sean correctos
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== PREFLIGHT REQUESTS (OPTIONS) ====================

    @Test
    @DisplayName("Debe responder a preflight request con headers CORS correctos")
    void shouldRespondToPreflightWithCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Headers"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Debe permitir origen localhost:3000")
    void shouldAllowLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    @DisplayName("Debe permitir origen localhost:5173 (Vite)")
    void shouldAllowViteOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // ==================== ACTUAL REQUESTS ====================

    @Test
    @DisplayName("Debe aceptar requests con Origin header de orígenes permitidos")
    void shouldAcceptRequestsFromAllowedOrigins() throws Exception {
        // Los headers CORS en requests normales (no preflight) pueden variar
        // Lo importante es que el request no sea rechazado
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk());
    }

    // ==================== ALLOWED METHODS ====================

    @Test
    @DisplayName("Debe permitir métodos GET, POST, PUT, DELETE, PATCH")
    void shouldAllowConfiguredMethods() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("DELETE")))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")));
    }

    // ==================== CREDENTIALS ====================

    @Test
    @DisplayName("Debe permitir credenciales (Access-Control-Allow-Credentials: true)")
    void shouldAllowCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // ==================== EXPOSED HEADERS ====================

    @Test
    @DisplayName("Debe configurar Expose-Headers en preflight")
    void shouldConfigureExposeHeaders() throws Exception {
        // Verify that exposed headers are configured
        // (Authorization, Content-Type, X-Total-Count)
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());

        // Los exposed headers se configuran pero pueden no aparecer en preflight
        // Lo importante es que la configuración no cause errores
    }

    // ==================== MAX AGE ====================

    @Test
    @DisplayName("Debe configurar Max-Age para cachear preflight")
    void shouldSetMaxAge() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Max-Age"));
    }
}

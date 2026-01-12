package com.quickstack.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.auth.dto.LoginRequest;
import com.quickstack.core.auth.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test End-to-End que simula el flujo completo de autenticación.
 * Usa @SpringBootTest para levantar toda la aplicación (con base de datos real).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Rollback después de cada test
class AuthenticationE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("E2E: Register → Login → Access Protected Endpoint")
    @Sql("/test-data-cleanup.sql") // Limpiar datos antes del test
    void shouldCompleteFullAuthenticationFlow() throws Exception {
        // ==================== PASO 1: REGISTER ====================
        RegisterRequest registerRequest = RegisterRequest.builder()
                .tenantName("Acme Corp")
                .tenantSlug("acme-e2e")
                .email("admin@acme-e2e.com")
                .password("password123")
                .userName("Admin User")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.email", is("admin@acme-e2e.com")))
                .andExpect(jsonPath("$.tenantName", is("Acme Corp")))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andReturn();

        // Extraer token del registro
        String registerResponseBody = registerResult.getResponse().getContentAsString();
        AuthResponse registerResponse = objectMapper.readValue(registerResponseBody, AuthResponse.class);
        String registerToken = registerResponse.getAccessToken();

        // ==================== PASO 2: LOGIN ====================
        LoginRequest loginRequest = LoginRequest.builder()
                .email("admin@acme-e2e.com")
                .password("password123")
                .tenantSlug("acme-e2e")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.email", is("admin@acme-e2e.com")))
                .andExpect(jsonPath("$.tenantName", is("Acme Corp")))
                .andReturn();

        // Extraer token del login
        String loginResponseBody = loginResult.getResponse().getContentAsString();
        AuthResponse loginResponse = objectMapper.readValue(loginResponseBody, AuthResponse.class);
        String loginToken = loginResponse.getAccessToken();

        // ==================== PASO 3: ACCESS PROTECTED ENDPOINT (con token de register) ====================
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // Solo el admin que acabamos de crear
                .andExpect(jsonPath("$[0].email", is("admin@acme-e2e.com")))
                .andExpect(jsonPath("$[0].role", is("ADMIN")));

        // ==================== PASO 4: ACCESS PROTECTED ENDPOINT (con token de login) ====================
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("admin@acme-e2e.com")));

        // ==================== PASO 5: GET USER BY ID ====================
        mockMvc.perform(get("/api/users/" + registerResponse.getUserId())
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("admin@acme-e2e.com")))
                .andExpect(jsonPath("$.name", is("Admin User")))
                .andExpect(jsonPath("$.role", is("ADMIN")));

        // ==================== PASO 6: ACCESS WITHOUT TOKEN (debe fallar) ====================
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        // ==================== PASO 7: ACCESS WITH INVALID TOKEN (debe fallar) ====================
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E2E: Login fallido no debe permitir acceso")
    @Sql("/test-data-cleanup.sql")
    void shouldRejectAccessWithoutValidLogin() throws Exception {
        // Intentar acceder a endpoint protegido sin token
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        // Intentar login con credenciales incorrectas (este sí debe ser 401 porque es un endpoint público que valida credenciales)
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("wrongpassword")
                .tenantSlug("nonexistent")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("E2E: Multi-tenancy - Usuarios de diferentes tenants están aislados")
    @Sql("/test-data-cleanup.sql")
    void shouldIsolateTenants() throws Exception {
        // Crear tenant 1
        RegisterRequest tenant1Request = RegisterRequest.builder()
                .tenantName("Tenant 1")
                .tenantSlug("tenant1-e2e")
                .email("admin@tenant1.com")
                .password("password123")
                .userName("Admin Tenant 1")
                .build();

        MvcResult tenant1Result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant1Request)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenant1ResponseBody = tenant1Result.getResponse().getContentAsString();
        AuthResponse tenant1Response = objectMapper.readValue(tenant1ResponseBody, AuthResponse.class);
        String tenant1Token = tenant1Response.getAccessToken();

        // Crear tenant 2
        RegisterRequest tenant2Request = RegisterRequest.builder()
                .tenantName("Tenant 2")
                .tenantSlug("tenant2-e2e")
                .email("admin@tenant2.com")
                .password("password123")
                .userName("Admin Tenant 2")
                .build();

        MvcResult tenant2Result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant2Request)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenant2ResponseBody = tenant2Result.getResponse().getContentAsString();
        AuthResponse tenant2Response = objectMapper.readValue(tenant2ResponseBody, AuthResponse.class);
        String tenant2Token = tenant2Response.getAccessToken();

        // Tenant 1 debe ver solo su usuario
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tenant1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("admin@tenant1.com")));

        // Tenant 2 debe ver solo su usuario
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tenant2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("admin@tenant2.com")));

        // Tenant 1 NO debe poder acceder al usuario de Tenant 2
        mockMvc.perform(get("/api/users/" + tenant2Response.getUserId())
                        .header("Authorization", "Bearer " + tenant1Token))
                .andExpect(status().isNotFound()); // UserService valida que el user pertenece al tenant
    }
}

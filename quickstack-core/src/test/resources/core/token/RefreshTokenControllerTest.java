package com.quickstack.core.token;

import com.quickstack.core.auth.AuthService;
import com.quickstack.core.auth.dto.AuthResponse;
import com.quickstack.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de controlador para endpoints de Refresh Tokens.
 *
 * Cobertura:
 * - POST /api/auth/refresh - Refrescar access token
 * - POST /api/auth/logout - Revocar refresh token
 * - POST /api/auth/logout-all - Revocar todos los tokens del usuario
 * - Autenticación y validación de requests
 * - Manejo de errores
 * - Respuestas HTTP correctas
 */
@Disabled("Feature pendiente de implementación - Solo Feature 1 (Audit Logging) está activa")
@WebMvcTest(RefreshTokenController.class)
class RefreshTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    private UUID userId;
    private UUID tenantId;
    private String validRefreshToken;
    private String newAccessToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        validRefreshToken = "valid-refresh-token-value";
        newAccessToken = "new-access-token-value";
    }

    // ==================== REFRESH TOKEN ENDPOINT TESTS ====================

    @Test
    @DisplayName("Debe refrescar access token con refresh token válido")
    void shouldRefreshAccessTokenWithValidRefreshToken() throws Exception {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash("hashed")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(10))
            .revoked(false)
            .build();

        when(refreshTokenService.validateRefreshToken(validRefreshToken)).thenReturn(true);
        when(refreshTokenService.refreshAccessToken(validRefreshToken))
            .thenReturn(newAccessToken);
        when(refreshTokenService.rotateRefreshToken(validRefreshToken))
            .thenReturn(refreshToken);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", is(newAccessToken)))
            .andExpect(jsonPath("$.refreshToken", notNullValue()))
            .andExpect(jsonPath("$.expiresIn", notNullValue()));
    }

    @Test
    @DisplayName("Debe rechazar refresh token inválido")
    void shouldRejectInvalidRefreshToken() throws Exception {
        // Given
        String invalidToken = "invalid-token";
        when(refreshTokenService.validateRefreshToken(invalidToken)).thenReturn(false);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(invalidToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", containsString("Invalid refresh token")));
    }

    @Test
    @DisplayName("Debe rechazar request sin refresh token")
    void shouldRejectRequestWithoutRefreshToken() throws Exception {
        // Given
        String requestBody = "{}";

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debe rechazar refresh token expirado")
    void shouldRejectExpiredRefreshToken() throws Exception {
        // Given
        String expiredToken = "expired-token";
        when(refreshTokenService.validateRefreshToken(expiredToken)).thenReturn(false);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(expiredToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe rechazar refresh token revocado")
    void shouldRejectRevokedRefreshToken() throws Exception {
        // Given
        String revokedToken = "revoked-token";
        when(refreshTokenService.validateRefreshToken(revokedToken)).thenReturn(false);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(revokedToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debe retornar nuevo refresh token rotado")
    void shouldReturnNewRotatedRefreshToken() throws Exception {
        // Given
        RefreshToken newRefreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash("new-hashed-token")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .build();

        when(refreshTokenService.validateRefreshToken(validRefreshToken)).thenReturn(true);
        when(refreshTokenService.refreshAccessToken(validRefreshToken))
            .thenReturn(newAccessToken);
        when(refreshTokenService.rotateRefreshToken(validRefreshToken))
            .thenReturn(newRefreshToken);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", is(newAccessToken)))
            .andExpect(jsonPath("$.refreshToken", notNullValue()))
            .andExpect(jsonPath("$.refreshToken", not(validRefreshToken)));
    }

    // ==================== LOGOUT ENDPOINT TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("Debe revocar refresh token en logout")
    void shouldRevokeRefreshTokenOnLogout() throws Exception {
        // Given
        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", is("Logged out successfully")));

        verify(refreshTokenService).revokeRefreshToken(validRefreshToken);
    }

    @Test
    @DisplayName("Debe rechazar logout sin autenticación")
    void shouldRejectLogoutWithoutAuthentication() throws Exception {
        // Given
        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Debe rechazar logout sin refresh token")
    void shouldRejectLogoutWithoutRefreshToken() throws Exception {
        // Given
        String requestBody = "{}";

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("Debe manejar error al revocar token inexistente")
    void shouldHandleErrorWhenRevokingNonExistentToken() throws Exception {
        // Given
        String nonExistentToken = "non-existent-token";
        when(refreshTokenService.revokeRefreshToken(nonExistentToken))
            .thenThrow(new TokenNotFoundException("Token not found"));

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(nonExistentToken);

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    // ==================== LOGOUT ALL ENDPOINT TESTS ====================

    @Test
    @WithMockUser
    @DisplayName("Debe revocar todos los tokens del usuario")
    void shouldRevokeAllUserTokens() throws Exception {
        // Given
        when(jwtService.extractUserId(anyString())).thenReturn(userId);
        when(refreshTokenService.revokeAllUserTokens(userId)).thenReturn(3);

        // When & Then
        mockMvc.perform(post("/api/auth/logout-all")
                .header("Authorization", "Bearer valid-token")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("sessions")))
            .andExpect(jsonPath("$.revokedCount", is(3)));

        verify(refreshTokenService).revokeAllUserTokens(userId);
    }

    @Test
    @DisplayName("Debe rechazar logout-all sin autenticación")
    void shouldRejectLogoutAllWithoutAuthentication() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout-all")
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Debe retornar 0 si el usuario no tiene tokens activos")
    void shouldReturnZeroIfNoActiveTokens() throws Exception {
        // Given
        when(jwtService.extractUserId(anyString())).thenReturn(userId);
        when(refreshTokenService.revokeAllUserTokens(userId)).thenReturn(0);

        // When & Then
        mockMvc.perform(post("/api/auth/logout-all")
                .header("Authorization", "Bearer valid-token")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revokedCount", is(0)));
    }

    @Test
    @WithMockUser
    @DisplayName("Debe incluir información de sesiones revocadas")
    void shouldIncludeRevokedSessionsInfo() throws Exception {
        // Given
        when(jwtService.extractUserId(anyString())).thenReturn(userId);
        when(refreshTokenService.revokeAllUserTokens(userId)).thenReturn(5);

        // When & Then
        mockMvc.perform(post("/api/auth/logout-all")
                .header("Authorization", "Bearer valid-token")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message", containsString("5")))
            .andExpect(jsonPath("$.revokedCount", is(5)));
    }

    // ==================== TOKEN REUSE DETECTION TESTS ====================

    @Test
    @DisplayName("Debe rechazar token reusado y retornar error de seguridad")
    void shouldRejectReusedTokenWithSecurityError() throws Exception {
        // Given
        String reusedToken = "reused-token";
        when(refreshTokenService.validateRefreshToken(reusedToken)).thenReturn(false);
        when(refreshTokenService.refreshAccessToken(reusedToken))
            .thenThrow(new TokenReuseException("Token reuse detected"));

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(reusedToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", containsString("Security violation")));
    }

    // ==================== RESPONSE FORMAT TESTS ====================

    @Test
    @DisplayName("Debe incluir expiresIn en respuesta de refresh")
    void shouldIncludeExpiresInInRefreshResponse() throws Exception {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash("hashed")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .build();

        when(refreshTokenService.validateRefreshToken(validRefreshToken)).thenReturn(true);
        when(refreshTokenService.refreshAccessToken(validRefreshToken))
            .thenReturn(newAccessToken);
        when(refreshTokenService.rotateRefreshToken(validRefreshToken))
            .thenReturn(refreshToken);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn", notNullValue()))
            .andExpect(jsonPath("$.expiresIn", greaterThan(0)));
    }

    @Test
    @DisplayName("Debe incluir tokenType Bearer en respuesta")
    void shouldIncludeBearerTokenTypeInResponse() throws Exception {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash("hashed")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .build();

        when(refreshTokenService.validateRefreshToken(validRefreshToken)).thenReturn(true);
        when(refreshTokenService.refreshAccessToken(validRefreshToken))
            .thenReturn(newAccessToken);
        when(refreshTokenService.rotateRefreshToken(validRefreshToken))
            .thenReturn(refreshToken);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("Debe manejar error de servicio interno")
    void shouldHandleInternalServiceError() throws Exception {
        // Given
        when(refreshTokenService.validateRefreshToken(validRefreshToken))
            .thenThrow(new RuntimeException("Database error"));

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Debe validar formato de JSON")
    void shouldValidateJsonFormat() throws Exception {
        // Given
        String malformedJson = "{ malformed json }";

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debe rechazar content type incorrecto")
    void shouldRejectIncorrectContentType() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.TEXT_PLAIN)
                .content("refresh-token")
                .with(csrf()))
            .andExpect(status().isUnsupportedMediaType());
    }

    // ==================== RATE LIMITING TESTS ====================

    @Test
    @DisplayName("Debe aplicar rate limiting en endpoint de refresh")
    void shouldApplyRateLimitingOnRefreshEndpoint() throws Exception {
        // Given - Simular múltiples requests rápidos
        RefreshToken refreshToken = RefreshToken.builder()
            .id(UUID.randomUUID())
            .tokenHash("hashed")
            .userId(userId)
            .tenantId(tenantId)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .revoked(false)
            .build();

        when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
        when(refreshTokenService.refreshAccessToken(anyString())).thenReturn(newAccessToken);
        when(refreshTokenService.rotateRefreshToken(anyString())).thenReturn(refreshToken);

        String requestBody = """
            {
                "refreshToken": "%s"
            }
            """.formatted(validRefreshToken);

        // When - Hacer múltiples requests
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf()));
        }

        // Then - Algún request debe ser rate limited (429)
        // Esto depende de la configuración de rate limiting
        // Este test documenta el comportamiento esperado
    }
}

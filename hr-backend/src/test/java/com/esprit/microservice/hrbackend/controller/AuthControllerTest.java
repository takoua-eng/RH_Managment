package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.AuthRequestDTO;
import com.esprit.microservice.hrbackend.dto.RefreshRequestDTO;
import com.esprit.microservice.hrbackend.dto.TokenResponseDTO;
import com.esprit.microservice.hrbackend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Codes HTTP renvoyés par l'authentification, et absence de détails techniques dans les réponses.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    private AuthRequestDTO demande(String utilisateur, String motDePasse) {
        AuthRequestDTO request = mock(AuthRequestDTO.class);
        lenient().when(request.getUsername()).thenReturn(utilisateur);
        lenient().when(request.getPassword()).thenReturn(motDePasse);
        return request;
    }

    // ---------------------------------------------------------------- connexion

    @Test
    @DisplayName("Connexion réussie : 200 avec les jetons")
    void connexionReussie() {
        TokenResponseDTO jetons = mock(TokenResponseDTO.class);
        when(authService.login("manager1", "secret")).thenReturn(jetons);

        ResponseEntity<Object> response = controller.login(demande("manager1", "secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(jetons);
    }

    @Test
    @DisplayName("Champs vides : 400, Keycloak n'est pas appelé")
    void champsVides() {
        ResponseEntity<Object> response = controller.login(demande("", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("Identifiants refusés par Keycloak : 401, sans détails techniques")
    void identifiantsRefuses() {
        when(authService.login("manager1", "faux"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "invalid_grant"));

        ResponseEntity<Object> response = controller.login(demande("manager1", "faux"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Identifiants invalides"));
    }

    @Test
    @DisplayName("Keycloak injoignable : 503, et non 401")
    void keycloakInjoignable() {
        when(authService.login("manager1", "secret"))
                .thenThrow(new ResourceAccessException("Connection refused: keycloak:8080"));

        ResponseEntity<Object> response = controller.login(demande("manager1", "secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // L'adresse interne de Keycloak ne doit pas apparaître dans la réponse
        assertThat(response.getBody().toString()).doesNotContain("keycloak:8080");
    }

    // ---------------------------------------------------------------- rafraîchissement

    @Test
    @DisplayName("Rafraîchissement sans jeton : 400")
    void rafraichissementSansJeton() {
        RefreshRequestDTO request = mock(RefreshRequestDTO.class);
        when(request.getRefreshToken()).thenReturn(" ");

        assertThat(controller.refresh(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Rafraîchissement refusé : 401")
    void rafraichissementRefuse() {
        RefreshRequestDTO request = mock(RefreshRequestDTO.class);
        when(request.getRefreshToken()).thenReturn("jeton-expire");
        when(authService.refreshToken("jeton-expire")).thenThrow(new RuntimeException("expired"));

        assertThat(controller.refresh(request).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------------- déconnexion

    @Test
    @DisplayName("Déconnexion : 204, même si Keycloak échoue")
    void deconnexionToujoursTerminee() {
        RefreshRequestDTO request = mock(RefreshRequestDTO.class);
        when(request.getRefreshToken()).thenReturn("jeton");
        doThrow(new RuntimeException("expired")).when(authService).logout("jeton");

        assertThat(controller.logout(request).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("Déconnexion sans corps de requête : 204")
    void deconnexionSansCorps() {
        assertThat(controller.logout(null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout(null);
    }
}
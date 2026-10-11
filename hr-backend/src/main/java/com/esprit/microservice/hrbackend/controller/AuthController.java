package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.AuthRequestDTO;
import com.esprit.microservice.hrbackend.dto.RefreshRequestDTO;
import com.esprit.microservice.hrbackend.dto.TokenResponseDTO;
import com.esprit.microservice.hrbackend.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

/**
 * Authentification via Keycloak : connexion, rafraîchissement du jeton, déconnexion.
 *
 * Les détails techniques des erreurs (réponses de Keycloak, messages d'exception) sont
 * écrits dans les journaux du serveur, jamais renvoyés au navigateur.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
 //todo
 // Test de la détection des changements par le pipeline

    private static final String ERROR = "error";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody AuthRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR, "Nom d'utilisateur et mot de passe requis"));
        }

        try {
            TokenResponseDTO tokenResponse = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(tokenResponse);

        } catch (HttpClientErrorException e) {
            // Keycloak a refusé la connexion (identifiants incorrects, compte désactivé…)
            log.warn("Connexion refusée par Keycloak pour '{}' : statut {}", request.getUsername(), e.getStatusCode());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(ERROR, "Identifiants invalides"));

        } catch (Exception e) {
            // Keycloak injoignable ou erreur inattendue : ce n'est pas la faute de l'utilisateur
            log.error("Service d'authentification indisponible", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(ERROR, "Service d'authentification momentanément indisponible"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(@RequestBody RefreshRequestDTO request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR, "Refresh token requis"));
        }

        try {
            TokenResponseDTO tokenResponse = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            log.info("Rafraîchissement du jeton refusé : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(ERROR, "Impossible de rafraîchir le token"));
        }
    }

    /**
     * La déconnexion côté navigateur a toujours lieu (le frontend efface ses jetons).
     * Si la fermeture de la session Keycloak échoue (jeton déjà expiré, par exemple),
     * l'incident est seulement journalisé : la réponse reste 204 No Content.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequestDTO request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        try {
            authService.logout(refreshToken);
        } catch (Exception e) {
            log.info("Fermeture de la session Keycloak impossible (jeton probablement expiré) : {}", e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
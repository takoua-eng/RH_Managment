package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.AuthRequestDTO;
import com.esprit.microservice.hrbackend.dto.RefreshRequestDTO;
import com.esprit.microservice.hrbackend.dto.TokenResponseDTO;
import com.esprit.microservice.hrbackend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDTO request) {
        try {
            if (request.getUsername() == null || request.getUsername().isBlank() ||
                request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Nom d'utilisateur et mot de passe requis"));
            }

            TokenResponseDTO tokenResponse = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(tokenResponse);
        } catch (HttpStatusCodeException e) {
            System.err.println("Keycloak Auth Error status: " + e.getStatusCode() + " body: " + e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Identifiants invalides ou accès refusé", "details", e.getResponseBodyAsString()));
        } catch (Exception e) {
            System.err.println("Keycloak Auth Exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Échec de l'authentification: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequestDTO request) {
        try {
            if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Refresh token requis"));
            }

            TokenResponseDTO tokenResponse = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Impossible de rafraîchir le token"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) RefreshRequestDTO request) {
        try {
            String refreshToken = request != null ? request.getRefreshToken() : null;
            authService.logout(refreshToken);
            return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
        } catch (Exception e) {
            // Even if Keycloak logout fails or token expired, complete local logout cleanly
            return ResponseEntity.ok(Map.of("message", "Déconnexion effectuée"));
        }
    }
}

package com.esprit.microservice.hrbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Rôles Keycloak (sans le préfixe ROLE_, ajouté par hasRole / hasAnyRole)
    private static final String ADMIN = "ADMIN";
    private static final String RH = "RH";
    private static final String MANAGER = "MANAGER";
    private static final String EMPLOYEE = "EMPLOYEE";
    private static final String[] TOUS_LES_ROLES = {ADMIN, RH, MANAGER, EMPLOYEE};

    @Value("${app.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // CSRF désactivé volontairement : API REST sans état (STATELESS, ci-dessous),
                // authentifiée par un jeton JWT envoyé dans l'en-tête Authorization, jamais
                // par un cookie. Un site tiers ne peut ni lire ce jeton ni l'ajouter à une
                // requête forgée : une attaque CSRF n'a donc pas de prise sur cette API.
                .csrf(AbstractHttpConfigurer::disable)

                .cors(Customizer.withDefaults())

                // Aucune session HTTP, donc aucun cookie de session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ===== Public =====
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // Candidature depuis la page publique (sans compte)
                        .requestMatchers(HttpMethod.POST, "/api/recruitment/candidates").permitAll()
                        // Photos : les balises <img> du navigateur n'envoient pas le jeton.
                        // Compromis accepté : seule la photo est exposée, pas les données de l'employé.
                        .requestMatchers(HttpMethod.GET, "/api/employees/*/photo").permitAll()
                        // Poignée de main WebSocket : l'authentification se fait ensuite par le jeton
                        // envoyé dans la trame STOMP CONNECT (voir WebSocketConfig)
                        .requestMatchers("/ws/**").permitAll()

                        // ===== Administration et statistiques =====
                        .requestMatchers("/api/admin/dashboard/**").hasAnyRole(ADMIN, RH)
                        .requestMatchers("/api/admin/**").hasRole(ADMIN)

                        // ===== Espaces par rôle =====
                        .requestMatchers("/api/rh/**").hasAnyRole(ADMIN, RH)
                        .requestMatchers("/api/manager/**").hasAnyRole(ADMIN, MANAGER)

                        // ===== Fonctionnalités communes (contrôles fins par @PreAuthorize) =====
                        .requestMatchers(
                                "/api/employees/**",
                                "/api/leaves/**",
                                "/api/trainings/**",
                                "/api/documents/**",
                                "/api/evaluations/**"
                        ).hasAnyRole(TOUS_LES_ROLES)

                        // ===== Tout le reste : utilisateur connecté =====
                        .anyRequest().authenticated()
                )

                // ===== Keycloak / JWT =====
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));

        return http.build();
    }

    /**
     * Convertit les rôles Keycloak (realm_access.roles) en rôles Spring Security.
     * Exemple : EMPLOYEE → ROLE_EMPLOYEE
     */
     JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        if (!(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()))
                .toList();
    }

    /**
     * CORS : origines autorisées fournies par la configuration (app.allowed-origins).
     * Pas d'envoi de cookies (allowCredentials = false) : l'API n'en utilise aucun.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
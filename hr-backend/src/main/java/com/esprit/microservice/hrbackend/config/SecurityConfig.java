package com.esprit.microservice.hrbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // CSRF désactivé car API REST avec JWT
                .csrf(AbstractHttpConfigurer::disable)

                // CORS
                .cors(Customizer.withDefaults())

                // Pas de session côté serveur
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // =========================
                        // PUBLIC / AUTH
                        // =========================
                        .requestMatchers("/api/auth/**")
                        .permitAll()

                        .requestMatchers("/api/public/**")
                        .permitAll()

                        .requestMatchers("/api/employees/*/photo")
                        .permitAll()

                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/recruitment/candidates")
                        .permitAll()

                        .requestMatchers("/ws/**")
                        .permitAll()

                        // =========================
                        // ADMIN & DASHBOARD STATS
                        // =========================
                        .requestMatchers("/api/admin/dashboard/**")
                        .hasAnyRole("ADMIN", "RH")

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // =========================
                        // RH
                        // =========================
                        .requestMatchers("/api/rh/**")
                        .hasAnyRole("ADMIN", "RH")

                        // =========================
                        // MANAGER
                        // =========================
                        .requestMatchers("/api/manager/**")
                        .hasAnyRole("ADMIN", "MANAGER")

                        // =========================
                        // EMPLOYEES
                        // =========================
                        .requestMatchers("/api/employees/**")
                        .hasAnyRole(
                                "ADMIN",
                                "RH",
                                "MANAGER",
                                "EMPLOYEE"
                        )

                        // =========================
                        // CONGES
                        // =========================
                        .requestMatchers("/api/leaves/**")
                        .hasAnyRole(
                                "ADMIN",
                                "RH",
                                "MANAGER",
                                "EMPLOYEE"
                        )

                        // =========================
                        // FORMATIONS
                        // =========================
                        .requestMatchers("/api/trainings/**")
                        .hasAnyRole(
                                "ADMIN",
                                "RH",
                                "MANAGER",
                                "EMPLOYEE"
                        )

                        // =========================
                        // DOCUMENTS
                        // =========================
                        .requestMatchers("/api/documents/**")
                        .hasAnyRole(
                                "ADMIN",
                                "RH",
                                "MANAGER",
                                "EMPLOYEE"
                        )

                        // =========================
                        // EVALUATIONS
                        // =========================
                        .requestMatchers("/api/evaluations/**")
                        .hasAnyRole(
                                "ADMIN",
                                "RH",
                                "MANAGER",
                                "EMPLOYEE"
                        )

                        // =========================
                        // TOUT LE RESTE
                        // =========================
                        .anyRequest()
                        .authenticated()
                )

                // =========================
                // KEYCLOAK / JWT
                // =========================
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthConverter()
                                )
                        )
                );

        return http.build();
    }

    /**
     * Convertit les rôles Keycloak en rôles Spring Security.
     *
     * Keycloak :
     * EMPLOYEE
     *
     * Spring Security :
     * ROLE_EMPLOYEE
     */
    private JwtAuthenticationConverter jwtAuthConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                this::extractAuthorities
        );

        return converter;
    }

    /**
     * Récupération des rôles depuis :
     *
     * realm_access.roles
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {

        Map<String, Object> realmAccess =
                jwt.getClaim("realm_access");

        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object rolesObject =
                realmAccess.get("roles");

        if (!(rolesObject instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }

        return roles.stream()
                .filter(role -> role instanceof String)
                .map(role ->
                        new SimpleGrantedAuthority(
                                "ROLE_" +
                                        role.toString().toUpperCase()
                        )
                )
                .collect(Collectors.toList());
    }

    /**
     * Configuration CORS Angular
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config =
                new CorsConfiguration();

        config.setAllowedOrigins(
                List.of("http://localhost:4200")
        );

        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "PATCH",
                        "OPTIONS"
                )
        );

        config.setAllowedHeaders(
                List.of("*")
        );

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                config
        );

        return source;
    }
}
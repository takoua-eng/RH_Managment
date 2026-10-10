package com.esprit.microservice.hrbackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversion des rôles Keycloak (realm_access.roles) en rôles Spring Security.
 */
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    private Jwt jeton(Map<String, Object> realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("jeton-de-test")
                .header("alg", "none")
                .subject("utilisateur-test");
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }

    private List<String> roles(Jwt jwt) {
        AbstractAuthenticationToken auth = config.jwtAuthConverter().convert(jwt);
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("Les rôles Keycloak deviennent des rôles Spring préfixés par ROLE_")
    void conversionDesRoles() {
        Jwt jwt = jeton(Map.of("roles", List.of("MANAGER", "employee")));

        assertThat(roles(jwt)).containsExactlyInAnyOrder("ROLE_MANAGER", "ROLE_EMPLOYEE");
    }

    @Test
    @DisplayName("Sans realm_access, l'utilisateur n'a aucun rôle")
    void sansRealmAccess() {
        assertThat(roles(jeton(null))).isEmpty();
    }

    @Test
    @DisplayName("Les valeurs qui ne sont pas du texte sont ignorées")
    void valeursInvalidesIgnorees() {
        Jwt jwt = jeton(Map.of("roles", List.of("RH", 42)));

        assertThat(roles(jwt)).containsExactly("ROLE_RH");
    }
}
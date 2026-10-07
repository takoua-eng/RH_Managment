package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Role;
import com.esprit.microservice.hrbackend.entity.Status;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final EmployeeRepository employeeRepository;

    /**
     * JIT Provisioning (Just-In-Time Provisioning):
     * Récupère ou crée automatiquement un enregistrement Employee correspondant au token Keycloak.
     *
     * @param jwt Le token JWT de l'utilisateur authentifié
     * @return L'entité Employee liée en base de données
     */
    @Transactional
    public Employee getOrCreateCurrentEmployee(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Utilisateur non authentifié.");
        }

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        // a. Recherche par keycloakId (sub)
        if (keycloakId != null && !keycloakId.isBlank()) {
            Optional<Employee> empByKeycloak = employeeRepository.findByKeycloakId(keycloakId);
            if (empByKeycloak.isPresent()) {
                return empByKeycloak.get();
            }
        }

        // b. Recherche par email (pour rattacher un compte employé existant créé manuellement)
        if (email != null && !email.isBlank()) {
            Optional<Employee> empByEmail = employeeRepository.findByEmail(email);
            if (empByEmail.isPresent()) {
                Employee existing = empByEmail.get();
                if (keycloakId != null && (existing.getKeycloakId() == null || existing.getKeycloakId().isBlank())) {
                    existing.setKeycloakId(keycloakId);
                    log.info("[KEYCLOAK LINK] Rattachement automatique du compte Keycloak sub={} à l'employé existant ID={} (email={})",
                            keycloakId, existing.getId(), email);
                    return employeeRepository.save(existing);
                }
                return existing;
            }
        }

        // c. Création automatique d'un nouvel Employee minimal (JIT Provisioning)
        String firstName = (givenName != null && !givenName.isBlank()) ? givenName : (preferredUsername != null ? preferredUsername : "Admin");
        String lastName = (familyName != null && !familyName.isBlank()) ? familyName : "User";
        String finalEmail = (email != null && !email.isBlank()) ? email : (preferredUsername != null ? preferredUsername + "@corp.com" : keycloakId + "@corp.com");

        // Détection du rôle à partir du token JWT
        Role defaultRole = Role.EMPLOYE;
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List) {
            List<?> roles = (List<?>) realmAccess.get("roles");
            if (roles.contains("ADMIN") || roles.contains("ADMINISTRATEUR")) {
                defaultRole = Role.ADMIN;
            } else if (roles.contains("RH")) {
                defaultRole = Role.RH;
            } else if (roles.contains("MANAGER")) {
                defaultRole = Role.MANAGER;
            }
        }

        Employee newEmployee = Employee.builder()
                .keycloakId(keycloakId)
                .firstName(firstName)
                .lastName(lastName)
                .email(finalEmail)
                .position("Non défini")
                .status(Status.ACTIVE)
                .role(defaultRole)
                .hireDate(LocalDate.now())
                .salary(25000.0)
                .availableLeaveDays(25)
                .phone("+216 20 000 000")
                .address("Adresse non spécifiée")
                .build();

        Employee saved = employeeRepository.save(newEmployee);
        log.info("[JIT PROVISIONING] Création automatique d'un nouvel employé en BDD pour l'utilisateur Keycloak sub={} (email={}, role={}, id={})",
                keycloakId, finalEmail, defaultRole, saved.getId());

        return saved;
    }
}

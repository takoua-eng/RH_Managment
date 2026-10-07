package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.TrainingDTO;
import com.esprit.microservice.hrbackend.dto.TrainingEnrollmentDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.TrainingEnrollment;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.TrainingService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final EmployeeRepository employeeRepository;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }


    // =========================================================
    // RÉCUPÉRER L'EMPLOYÉ CONNECTÉ
    // =========================================================

    private Employee getAuthenticatedEmployee(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt)) {

            throw new AccessDeniedException(
                    "Utilisateur non authentifié."
            );
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        String keycloakId = jwt.getSubject();

        if (keycloakId != null) {

            var employeeByKeycloak =
                    employeeRepository.findByKeycloakId(keycloakId);

            if (employeeByKeycloak.isPresent()) {
                return employeeByKeycloak.get();
            }
        }

        /*
         * Fallback avec l'email du JWT.
         */
        String email =
                jwt.getClaimAsString("email");

        if (email != null && !email.isBlank()) {

            return employeeRepository
                    .findByEmail(email)
                    .map(employee -> {

                        /*
                         * Si l'employé n'avait pas encore
                         * de Keycloak ID, on le rattache
                         * automatiquement.
                         */
                        if (employee.getKeycloakId() == null
                                || employee.getKeycloakId().isBlank()) {

                            employee.setKeycloakId(keycloakId);

                            return employeeRepository.save(
                                    employee
                            );
                        }

                        return employee;
                    })
                    .orElseThrow(() ->
                            new AccessDeniedException(
                                    "Aucun employé trouvé avec l'email : "
                                            + email
                            )
                    );
        }

        throw new AccessDeniedException(
                "Employé authentifié introuvable."
        );
    }


    // =========================================================
    // VÉRIFIER L'ACCÈS À UN EMPLOYÉ
    // =========================================================

    private void checkEmployeeAccess(
            Long employeeId,
            Authentication authentication) {

        boolean isAdminOrRh =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        authority.equals("ROLE_ADMIN")
                                                || authority.equals("ROLE_RH")
                        );

        boolean isManager =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        authority.equals("ROLE_MANAGER")
                        );

        Employee authenticatedEmployee =
                getAuthenticatedEmployee(authentication);

        if (isAdminOrRh) {
            return; // Admins and RH can access everyone
        }

        if (isManager) {
            if (authenticatedEmployee.getId().equals(employeeId)) {
                return; // Can access themselves
            }

            Employee targetEmployee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new AccessDeniedException("Employé introuvable."));
                    
            if (targetEmployee.getManager() == null || !targetEmployee.getManager().getId().equals(authenticatedEmployee.getId())) {
                throw new AccessDeniedException("Vous n'êtes pas autorisé à accéder aux formations de cet employé.");
            }
            return;
        }

        // EMPLOYEE peut uniquement consulter ses propres formations.
        if (!authenticatedEmployee.getId().equals(employeeId)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à accéder aux formations de cet employé.");
        }
    }


    // =========================================================
    // CATALOGUE DES FORMATIONS
    // =========================================================

    @GetMapping
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<List<TrainingDTO>> getCatalog() {

        return ResponseEntity.ok(
                trainingService.getAllTrainings()
        );
    }

    // =========================================================
    // ADMIN/RH - CRÉER UNE FORMATION
    // =========================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<TrainingDTO> createTraining(@RequestBody TrainingDTO trainingDTO) {
        return ResponseEntity.ok(trainingService.createTraining(trainingDTO));
    }

    // =========================================================
    // ADMIN/RH - METTRE À JOUR UNE FORMATION
    // =========================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<TrainingDTO> updateTraining(@PathVariable Long id, @RequestBody TrainingDTO trainingDTO) {
        return ResponseEntity.ok(trainingService.updateTraining(id, trainingDTO));
    }

    // =========================================================
    // ADMIN/RH - SUPPRIMER UNE FORMATION
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<Void> deleteTraining(@PathVariable Long id) {
        trainingService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }


    // =========================================================
    // MES INSCRIPTIONS
    // =========================================================

    @GetMapping("/my-enrollments")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<List<TrainingEnrollmentDTO>> getMyEnrollments(
            Authentication authentication) {

        Employee employee =
                getAuthenticatedEmployee(authentication);

        return ResponseEntity.ok(
                trainingService.getEnrollmentsByEmployee(
                        employee.getId()
                )
        );
    }


    // =========================================================
    // INSCRIPTIONS D'UN EMPLOYÉ
    // =========================================================

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<List<TrainingEnrollmentDTO>>
    getEmployeeEnrollments(
            @PathVariable Long employeeId,
            Authentication authentication) {

        checkEmployeeAccess(
                employeeId,
                authentication
        );

        return ResponseEntity.ok(
                trainingService.getEnrollmentsByEmployee(
                        employeeId
                )
        );
    }


    // =========================================================
    // S'INSCRIRE À UNE FORMATION
    // =========================================================

    @PostMapping("/enroll/{trainingId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<TrainingEnrollmentDTO> enroll(
            @PathVariable Long trainingId,
            Authentication authentication) {

        Employee employee =
                getAuthenticatedEmployee(authentication);

        return ResponseEntity.ok(
                trainingService.enroll(
                        employee.getId(),
                        trainingId
                )
        );
    }


    // =========================================================
    // METTRE À JOUR LA PROGRESSION
    // =========================================================

    @PostMapping("/progress/{enrollmentId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<TrainingEnrollmentDTO> progress(
            @PathVariable Long enrollmentId,
            Authentication authentication) {

        TrainingEnrollment enrollment =
                trainingService.getEnrollmentEntityById(
                        enrollmentId
                );

        if (enrollment == null
                || enrollment.getEmployee() == null) {

            throw new AccessDeniedException(
                    "Inscription de formation introuvable."
            );
        }

        Employee authenticatedEmployee = getAuthenticatedEmployee(authentication);
        if (!authenticatedEmployee.getId().equals(enrollment.getEmployee().getId())) {
            throw new AccessDeniedException(
                    "Vous n'êtes pas autorisé à modifier la progression de cette formation."
            );
        }

        return ResponseEntity.ok(
                trainingService.incrementProgress(
                        enrollmentId
                )
        );
    }


    // =========================================================
    // TÉLÉCHARGER LE CERTIFICAT
    // =========================================================

    @GetMapping("/certificate/{enrollmentId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<org.springframework.core.io.Resource> downloadCertificate(
            @PathVariable Long enrollmentId,
            Authentication authentication) {

        TrainingEnrollment enrollment =
                trainingService.getEnrollmentEntityById(
                        enrollmentId
                );

        if (enrollment == null
                || enrollment.getEmployee() == null) {

            throw new AccessDeniedException(
                    "Inscription de formation introuvable."
            );
        }

        checkEmployeeAccess(
                enrollment.getEmployee().getId(),
                authentication
        );

        org.springframework.core.io.Resource resource =
                trainingService.getCertificateResource(
                        enrollmentId
                );

        HttpHeaders headers =
                new HttpHeaders();

        headers.add(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"certificat_"
                        + enrollmentId
                        + ".pdf\""
        );

        headers.setContentType(
                MediaType.APPLICATION_PDF
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
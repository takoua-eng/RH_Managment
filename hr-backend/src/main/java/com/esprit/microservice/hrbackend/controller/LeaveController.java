package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.dto.LeaveCalendarDTO;
import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.dto.LeaveStatsDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.EmployeeService;
import com.esprit.microservice.hrbackend.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demandes, consultation et annulation des congés.
 *
 * Les décisions (acceptation et refus) ne passent PAS par ce contrôleur :
 * elles sont prises par le manager de l'employé via LeaveDecisionController
 * (PUT /api/leave-decisions/{id}/approve et /reject), qui vérifie les droits,
 * exige un motif en cas de refus et décompte le solde de congés.
 */
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;


    // =========================================================
    // OBTENIR TOUS LES CONGÉS (ADMIN / RH)
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<List<LeaveDTO>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }


    // =========================================================
    // DEMANDER UN CONGÉ
    // =========================================================

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveDTO> requestLeave(
            @Valid @RequestBody LeaveDTO dto,
            Authentication authentication) {

        // Un EMPLOYEE ne peut créer une demande que pour lui-même
        if (isEmployee(authentication)) {
            EmployeeResponseDTO employee = employeeService.getEmployeeById(dto.getEmployeeId());
            verifyEmployeeAccess(employee, authentication,
                    "Vous n'êtes pas autorisé à demander un congé pour un autre employé.");
        }

        LeaveDTO result = leaveService.requestLeave(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    // =========================================================
    // HISTORIQUE D'UN EMPLOYÉ
    // =========================================================

    @GetMapping("/employee/{employeeId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveDTO>> getLeaveHistory(
            @PathVariable Long employeeId,
            Authentication authentication) {

        // Un EMPLOYEE ne peut consulter que son propre historique
        if (isEmployee(authentication)) {
            EmployeeResponseDTO employee = employeeService.getEmployeeById(employeeId);
            verifyEmployeeAccess(employee, authentication,
                    "Vous n'êtes pas autorisé à accéder aux données de cet employé.");
        }

        return ResponseEntity.ok(leaveService.getLeaveHistory(employeeId));
    }


    // =========================================================
    // STATISTIQUES
    // =========================================================

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveStatsDTO> getStats() {
        return ResponseEntity.ok(leaveService.getStats());
    }


    // =========================================================
    // CALENDRIER
    // =========================================================

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveCalendarDTO>> getCalendarAbsences(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {

        return ResponseEntity.ok(leaveService.getCalendarAbsences(year, month));
    }


    // =========================================================
    // CONGÉS EN ATTENTE
    // =========================================================

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getPendingLeaves() {
        return ResponseEntity.ok(leaveService.getPendingLeaves());
    }


    // =========================================================
    // ANNULER UN CONGÉ
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'EMPLOYEE')")
    public ResponseEntity<Void> cancelLeave(@PathVariable Long id) {
        // La vérification du propriétaire du congé est faite dans LeaveService.cancelLeave()
        leaveService.cancelLeave(id);
        return ResponseEntity.noContent().build();
    }


    // =========================================================
    // VÉRIFICATIONS D'ACCÈS
    // =========================================================

    private boolean isEmployee(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_EMPLOYEE"));
    }

    /**
     * Vérifie que l'utilisateur connecté est bien l'employé concerné.
     * Identification par l'identifiant Keycloak, ou à défaut par l'email
     * (dans ce cas, l'identifiant Keycloak est enregistré pour les fois suivantes).
     */
    private void verifyEmployeeAccess(EmployeeResponseDTO employee,
                                      Authentication authentication,
                                      String messageRefus) {

        if (employee == null) {
            throw new AccessDeniedException("Employé introuvable.");
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("JWT invalide.");
        }

        String tokenKeycloakId = jwt.getSubject();

        // 1. Identification par l'identifiant Keycloak
        if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(tokenKeycloakId)) {
            return;
        }

        // 2. Identification par l'email, puis association du compte Keycloak
        String tokenEmail = jwt.getClaimAsString("email");
        if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(tokenEmail)) {
            employeeRepository.findById(employee.getId()).ifPresent(emp -> lierCompteKeycloak(emp, tokenKeycloakId));
            return;
        }

        throw new AccessDeniedException(messageRefus);
    }

    private void lierCompteKeycloak(Employee emp, String keycloakId) {
        if (emp.getKeycloakId() == null || emp.getKeycloakId().isEmpty()) {
            emp.setKeycloakId(keycloakId);
            employeeRepository.save(emp);
        }
    }
}
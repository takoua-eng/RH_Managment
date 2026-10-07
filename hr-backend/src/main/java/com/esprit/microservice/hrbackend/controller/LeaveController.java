package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.dto.LeaveCalendarDTO;
import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.dto.LeaveStatsDTO;
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
import java.util.Map;
import java.util.Optional;

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

        /*
         * EMPLOYEE :
         * il peut uniquement créer un congé pour lui-même.
         */
        if (isEmployee(authentication)) {

            EmployeeResponseDTO employee =
                    employeeService.getEmployeeById(dto.getEmployeeId());

            verifyEmployeeAccess(
                    employee,
                    authentication,
                    dto
            );
        }

        LeaveDTO result =
                leaveService.requestLeave(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }


    // =========================================================
    // APPROBATION RH
    // =========================================================

    @PutMapping("/{id}/approve-rh")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<LeaveDTO> approveByRH(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveService.approveByRH(id)
        );
    }


    // =========================================================
    // APPROBATION MANAGER
    // =========================================================

    @PutMapping("/{id}/approve-manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<LeaveDTO> approveByManager(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveService.approveByManager(id)
        );
    }


    // =========================================================
    // APPROBATION
    // =========================================================

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<LeaveDTO> approveLeave(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveService.approveByRH(id)
        );
    }


    // =========================================================
    // REFUS
    // =========================================================

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<LeaveDTO> rejectLeave(
            @PathVariable Long id,
            @RequestParam(
                    value = "reason",
                    required = false
            ) String reasonParam,
            @RequestBody(
                    required = false
            ) Map<String, String> body) {

        String reason = reasonParam;

        if (reason == null && body != null) {
            reason = body.get("reason");
        }

        return ResponseEntity.ok(
                leaveService.rejectLeave(id, reason)
        );
    }


    // =========================================================
    // HISTORIQUE D'UN EMPLOYÉ
    // =========================================================

    @GetMapping("/employee/{employeeId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveDTO>> getLeaveHistory(
            @PathVariable Long employeeId,
            Authentication authentication) {

        /*
         * EMPLOYEE :
         * il peut uniquement consulter son propre historique.
         */
        if (isEmployee(authentication)) {

            verifyEmployeeAccess(
                    employeeId,
                    authentication
            );
        }

        return ResponseEntity.ok(
                leaveService.getLeaveHistory(employeeId)
        );
    }


    // =========================================================
    // STATISTIQUES
    // =========================================================

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveStatsDTO> getStats() {

        return ResponseEntity.ok(
                leaveService.getStats()
        );
    }


    // =========================================================
    // CALENDRIER
    // =========================================================

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveCalendarDTO>> getCalendarAbsences(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {

        return ResponseEntity.ok(
                leaveService.getCalendarAbsences(
                        year,
                        month
                )
        );
    }


    // =========================================================
    // CONGÉS EN ATTENTE
    // =========================================================

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<List<LeaveDTO>> getPendingLeaves() {

        return ResponseEntity.ok(
                leaveService.getPendingLeaves()
        );
    }


    // =========================================================
    // ANNULER UN CONGÉ
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'EMPLOYEE')")
    public ResponseEntity<Void> cancelLeave(
            @PathVariable Long id) {

        /*
         * La vérification propriétaire du congé
         * est déjà réalisée dans LeaveService.cancelLeave().
         */
        leaveService.cancelLeave(id);

        return ResponseEntity.noContent().build();
    }


    // =========================================================
    // VÉRIFIER RÔLE EMPLOYEE
    // =========================================================

    private boolean isEmployee(
            Authentication authentication) {

        return authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(
                        authority ->
                                authority.equals("ROLE_EMPLOYEE")
                );
    }


    // =========================================================
    // VÉRIFIER ACCÈS EMPLOYÉ PAR ID
    // =========================================================

    private void verifyEmployeeAccess(
            Long employeeId,
            Authentication authentication) {

        EmployeeResponseDTO employee =
                employeeService.getEmployeeById(employeeId);

        verifyEmployeeAccess(
                employee,
                authentication
        );
    }


    // =========================================================
    // VÉRIFIER ACCÈS EMPLOYÉ
    // =========================================================

    private void verifyEmployeeAccess(
            EmployeeResponseDTO employee,
            Authentication authentication) {

        if (employee == null) {
            throw new AccessDeniedException(
                    "Employé introuvable."
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof Jwt)) {
            throw new AccessDeniedException(
                    "JWT invalide."
            );
        }

        Jwt jwt = (Jwt) principal;
        String tokenKeycloakId =
                jwt.getSubject();

        boolean authorized = false;
        if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(tokenKeycloakId)) {
            authorized = true;
        } else {
            String tokenEmail = jwt.getClaimAsString("email");
            if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(tokenEmail)) {
                authorized = true;
                Optional<com.esprit.microservice.hrbackend.entity.Employee> empOpt = employeeRepository.findById(employee.getId());
                if (empOpt.isPresent()) {
                    com.esprit.microservice.hrbackend.entity.Employee emp = empOpt.get();
                    if (emp.getKeycloakId() == null || emp.getKeycloakId().isEmpty()) {
                        emp.setKeycloakId(tokenKeycloakId);
                        employeeRepository.save(emp);
                    }
                }
            }
        }

        if (!authorized) {
            throw new AccessDeniedException(
                    "Vous n'êtes pas autorisé à accéder aux données de cet employé."
            );
        }
    }

    private void verifyEmployeeAccess(
            EmployeeResponseDTO employee,
            Authentication authentication,
            LeaveDTO dto) {

        if (employee == null) {
            throw new AccessDeniedException("Employé introuvable.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt)) {
            throw new AccessDeniedException("JWT invalide.");
        }

        Jwt jwt = (Jwt) principal;
        String tokenKeycloakId = jwt.getSubject();
        
        boolean authorized = false;
        if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(tokenKeycloakId)) {
            authorized = true;
        } else {
            String tokenEmail = jwt.getClaimAsString("email");
            if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(tokenEmail)) {
                authorized = true;
                Optional<com.esprit.microservice.hrbackend.entity.Employee> empOpt = employeeRepository.findById(dto.getEmployeeId());
                if (empOpt.isPresent()) {
                    com.esprit.microservice.hrbackend.entity.Employee emp = empOpt.get();
                    if (emp.getKeycloakId() == null || emp.getKeycloakId().isEmpty()) {
                        emp.setKeycloakId(tokenKeycloakId);
                        employeeRepository.save(emp);
                    }
                }
            }
        }

        if (!authorized) {
            throw new AccessDeniedException("You are not authorized to request leave for another employee");
        }
    }
}
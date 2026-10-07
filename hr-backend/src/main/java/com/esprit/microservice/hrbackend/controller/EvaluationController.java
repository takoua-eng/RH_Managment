package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EvaluationDTO;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EmployeeRepository employeeRepository;

    // =========================================================
    // MANAGER ENDPOINTS
    // =========================================================

    @PostMapping("/manager")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<EvaluationDTO> createEvaluation(@Valid @RequestBody EvaluationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.createEvaluation(dto));
    }

    @PutMapping("/manager/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<EvaluationDTO> updateEvaluation(@PathVariable Long id, @Valid @RequestBody EvaluationDTO dto) {
        return ResponseEntity.ok(evaluationService.updateEvaluation(id, dto));
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<EvaluationDTO>> getManagerEvaluations(@PathVariable Long managerId, Authentication authentication) {
        // En vrai système on vérifierait que le managerId correspond au JWT si pas ADMIN
        return ResponseEntity.ok(evaluationService.getEvaluationsByManager(managerId));
    }

    @DeleteMapping("/manager/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteEvaluation(@PathVariable Long id) {
        evaluationService.deleteEvaluation(id);
        return ResponseEntity.noContent().build();
    }


    // =========================================================
    // EMPLOYEE ENDPOINTS
    // =========================================================

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<List<EvaluationDTO>> getEmployeeEvaluations(@PathVariable Long employeeId, Authentication authentication) {
        checkEmployeeAccess(employeeId, authentication);
        return ResponseEntity.ok(evaluationService.getEvaluationsByEmployee(employeeId));
    }

    private void checkEmployeeAccess(Long employeeId, Authentication authentication) {
        boolean hasElevatedRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ROLE_MANAGER") || authority.equals("ROLE_RH"));

        if (!hasElevatedRole) {
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof Jwt)) throw new AccessDeniedException("JWT invalide.");
            
            Jwt jwt = (Jwt) principal;
            String keycloakId = jwt.getSubject();
            String email = jwt.getClaimAsString("email");

            com.esprit.microservice.hrbackend.entity.Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new AccessDeniedException("Employé introuvable."));

            boolean authorized = false;
            if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(keycloakId)) {
                authorized = true;
            } else if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(email)) {
                authorized = true;
            }

            if (!authorized) {
                throw new AccessDeniedException("Vous n'êtes pas autorisé à accéder aux évaluations de cet employé.");
            }
        }
    }
}

package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EmployeeRequestDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final com.esprit.microservice.hrbackend.service.CurrentUserService currentUserService;


    // =========================================================
    // GET ALL EMPLOYEES
    // ADMIN / RH / MANAGER uniquement
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<Page<EmployeeResponseDTO>> getAllEmployees(
            @PageableDefault(size = 10, sort = "id", direction = org.springframework.data.domain.Sort.Direction.ASC) Pageable pageable,
            @RequestParam(value = "search", required = false) String search) {

        return ResponseEntity.ok(
                employeeService.getAllEmployees(pageable, search)
        );
    }


    // =========================================================
    // GET CURRENT EMPLOYEE (/api/employees/me)
    // =========================================================

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeResponseDTO> getCurrentEmployee(
            Authentication authentication) {

        Jwt jwt = getJwt(authentication);
        com.esprit.microservice.hrbackend.entity.Employee currentEmployee = currentUserService.getOrCreateCurrentEmployee(jwt);
        return ResponseEntity.ok(com.esprit.microservice.hrbackend.mapper.EmployeeMapper.toResponseDTO(currentEmployee));
    }

    // =========================================================
    // UPDATE CURRENT EMPLOYEE PROFILE (/api/employees/me)
    // =========================================================

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeResponseDTO> updateCurrentEmployee(
            @Valid @RequestBody EmployeeRequestDTO dto,
            Authentication authentication) {

        Jwt jwt = getJwt(authentication);
        com.esprit.microservice.hrbackend.entity.Employee currentEmployee = currentUserService.getOrCreateCurrentEmployee(jwt);
        return ResponseEntity.ok(employeeService.updateEmployee(currentEmployee.getId(), dto));
    }


    // =========================================================
    // GET EMPLOYEE BY ID
    // ADMIN / RH / MANAGER
    // EMPLOYEE uniquement son propre profil
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(
            @PathVariable Long id,
            Authentication authentication) {

        EmployeeResponseDTO employee =
                employeeService.getEmployeeById(id);

        if (isEmployee(authentication)) {

            verifyEmployeeAccess(
                    employee,
                    authentication
            );
        }

        return ResponseEntity.ok(employee);
    }


    // =========================================================
    // CREATE EMPLOYEE
    // ADMIN / RH
    // =========================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody EmployeeRequestDTO dto) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(dto));
    }


    // =========================================================
    // UPDATE EMPLOYEE
    // ADMIN / RH
    // EMPLOYEE uniquement son propre profil
    // =========================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequestDTO dto,
            Authentication authentication) {

        boolean isFullAdminOrRh = authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_RH"));

        if (!isFullAdminOrRh) {
            EmployeeResponseDTO employee = employeeService.getEmployeeById(id);
            verifyEmployeeAccess(employee, authentication);
        }

        return ResponseEntity.ok(
                employeeService.updateEmployee(id, dto)
        );
    }


    // =========================================================
    // DELETE EMPLOYEE
    // ADMIN uniquement
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable Long id) {

        employeeService.deleteEmployee(id);

        return ResponseEntity.noContent().build();
    }


    // =========================================================
    // UPLOAD PHOTO
    // ADMIN / RH / EMPLOYEE
    // EMPLOYEE uniquement sa propre photo
    // =========================================================

    @PostMapping("/me/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> uploadMyPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        Jwt jwt = getJwt(authentication);
        Employee employee = currentUserService.getOrCreateCurrentEmployee(jwt);
        employeeService.uploadPhoto(employee.getId(), file);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Void> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (isEmployee(authentication)) {

            EmployeeResponseDTO employee =
                    employeeService.getEmployeeById(id);

            verifyEmployeeAccess(
                    employee,
                    authentication
            );
        }

        employeeService.uploadPhoto(
                id,
                file
        );

        return ResponseEntity.ok().build();
    }


    // =========================================================
    // GET PHOTO
    // =========================================================

    @GetMapping("/{id}/photo")
    public ResponseEntity<org.springframework.core.io.Resource> getPhoto(
            @PathVariable Long id) {

        org.springframework.core.io.Resource resource =
                employeeService.getPhotoResource(id);

        String contentType =
                employeeService.getPhotoContentType(id);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg")
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "max-age=604800, must-revalidate"
                )
                .body(resource);
    }


    // =========================================================
    // AFFECTER UN MANAGER À UN EMPLOYÉ
    // ADMIN / RH uniquement
    // =========================================================

    /**
     * Affecte ou change le manager d'un employé.
     * Corps : { "managerId": 5 } ou { "managerId": null } pour désaffecter.
     *
     * PUT /api/employees/{id}/manager
     */
    @PutMapping("/{id}/manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<EmployeeResponseDTO> assignManager(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {

        Long managerId = null;
        if (body.get("managerId") != null) {
            managerId = Long.valueOf(body.get("managerId").toString());
        }

        return ResponseEntity.ok(
                employeeService.assignManager(id, managerId)
        );
    }


    // =========================================================
    // LISTE DES MANAGERS (pour le dropdown Admin/RH)
    // =========================================================

    /**
     * Retourne les employés pouvant être désignés comme managers
     * (ceux ayant des subordonnés ou avec "manager" dans le poste).
     *
     * GET /api/employees/managers
     */
    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<java.util.List<EmployeeResponseDTO>> getManagers() {
        return ResponseEntity.ok(
                employeeService.getManagers()
        );
    }


    // =========================================================
    // ÉQUIPE D'UN MANAGER (vue Admin/RH)
    // =========================================================

    /**
     * Retourne tous les employés rattachés à un manager donné.
     *
     * GET /api/managers/{managerId}/team
     */
    @GetMapping("/by-manager/{managerId}/team")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<java.util.List<EmployeeResponseDTO>> getManagerTeam(
            @PathVariable Long managerId) {
        return ResponseEntity.ok(
                employeeService.getManagerTeam(managerId)
        );
    }


    // =========================================================
    // CHECK EMPLOYEE ROLE
    // =========================================================

    private boolean isEmployee(
            Authentication authentication) {

        return authentication
                .getAuthorities()
                .stream()
                .anyMatch(
                        authority ->
                                authority
                                        .getAuthority()
                                        .equals("ROLE_EMPLOYEE")
                );
    }


    // =========================================================
    // CHECK OWNERSHIP
    // =========================================================

    private void verifyEmployeeAccess(
            EmployeeResponseDTO employee,
            Authentication authentication) {

        if (employee == null) {

            throw new AccessDeniedException(
                    "Employé introuvable."
            );
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String tokenKeycloakId = jwt.getSubject();
        boolean authorized = false;

        if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(tokenKeycloakId)) {
            authorized = true;
        } else {
            String tokenEmail = jwt.getClaimAsString("email");
            if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(tokenEmail)) {
                authorized = true;
            }
        }

        if (!authorized) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this profile");
        }
    }


    // =========================================================
    // GET JWT
    // =========================================================

    private Jwt getJwt(
            Authentication authentication) {

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof Jwt)) {

            throw new AccessDeniedException(
                    "JWT invalide ou utilisateur non authentifié."
            );
        }

        return (Jwt) principal;
    }
}
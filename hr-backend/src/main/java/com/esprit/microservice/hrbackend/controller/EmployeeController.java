package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EmployeeRequestDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<Page<EmployeeResponseDTO>> getAllEmployees(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(employeeService.getAllEmployees(pageable, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYE')")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeById(
            @PathVariable Long id,
            Authentication authentication) {
        
        EmployeeResponseDTO employee = employeeService.getEmployeeById(id);
        
        // Custom security logic: if the user only has the EMPLOYE role,
        // they can only access their own profile.
        boolean isEmployeeOnly = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_RH") || auth.equals("ROLE_MANAGER"));

        if (isEmployeeOnly) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String tokenKeycloakId = jwt.getSubject();
            if (employee.getKeycloakId() == null || !employee.getKeycloakId().equals(tokenKeycloakId)) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this profile");
            }
        }

        return ResponseEntity.ok(employee);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<EmployeeResponseDTO> createEmployee(@Valid @RequestBody EmployeeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequestDTO dto) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<Void> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        employeeService.uploadPhoto(id, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable Long id) {
        byte[] photoBytes = employeeService.getPhoto(id);
        String contentType = employeeService.getPhotoContentType(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800, must-revalidate")
                .body(photoBytes);
    }
}

package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.NotificationDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping({"/api/employee/{employeeId}/notifications", "/api/manager/{employeeId}/notifications"})
@RequiredArgsConstructor
public class EmployeeNotificationController {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;

    private void checkEmployeeAccess(Long employeeId, Authentication authentication) {
        boolean hasElevatedRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ROLE_RH"));

        if (!hasElevatedRole) {
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof Jwt)) throw new AccessDeniedException("JWT invalide.");
            
            Jwt jwt = (Jwt) principal;
            String keycloakId = jwt.getSubject();
            String email = jwt.getClaimAsString("email");

            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new AccessDeniedException("Employé introuvable."));

            boolean authorized = false;
            if (employee.getKeycloakId() != null && employee.getKeycloakId().equals(keycloakId)) {
                authorized = true;
            } else if (employee.getEmail() != null && employee.getEmail().equalsIgnoreCase(email)) {
                authorized = true;
            }

            if (!authorized) {
                throw new AccessDeniedException("Vous n'êtes pas autorisé à accéder aux notifications de cet employé.");
            }
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<NotificationDTO>> getNotifications(@PathVariable Long employeeId, Authentication authentication) {
        checkEmployeeAccess(employeeId, authentication);
        return ResponseEntity.ok(notificationService.getNotifications(employeeId));
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long employeeId, Authentication authentication) {
        checkEmployeeAccess(employeeId, authentication);
        notificationService.markAllAsRead(employeeId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{notifId}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long employeeId,
            @PathVariable Long notifId,
            Authentication authentication) {
        checkEmployeeAccess(employeeId, authentication);
        notificationService.markAsRead(notifId);
        return ResponseEntity.ok().build();
    }

    // Endpoint for testing/simulating new training available for this employee
    @PostMapping("/training/trigger")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<Void> triggerTrainingNotification(@PathVariable Long employeeId, Authentication authentication) {
        checkEmployeeAccess(employeeId, authentication);
        notificationService.notifyManager(employeeId, "Une nouvelle formation est disponible au catalogue.", NotificationType.TRAINING);
        return ResponseEntity.ok().build();
    }
}

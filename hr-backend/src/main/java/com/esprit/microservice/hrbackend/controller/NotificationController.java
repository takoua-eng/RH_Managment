package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.NotificationDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;

    private Optional<Employee> getAuthenticatedEmployee(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        Optional<Employee> emp = employeeRepository.findByKeycloakId(keycloakId);
        if (emp.isPresent()) {
            return emp;
        }

        if (email != null && !email.isBlank()) {
            emp = employeeRepository.findByEmailIgnoreCase(email);
            if (emp.isPresent()) {
                return emp;
            }
        }

        log.warn("[NOTIFICATIONS] Connected Keycloak user (id={}, email={}) has no linked Employee entity.", keycloakId, email);
        return Optional.empty();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        
        Optional<Employee> emp = getAuthenticatedEmployee(authentication);
        if (emp.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        return ResponseEntity.ok(notificationService.getNotifications(emp.get().getId(), limit));
    }

    @GetMapping("/me/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getMyUnreadCount(Authentication authentication) {
        Optional<Employee> emp = getAuthenticatedEmployee(authentication);
        if (emp.isEmpty()) {
            return ResponseEntity.ok(Map.of("unreadCount", 0L));
        }

        long count = notificationService.getUnreadCount(emp.get().getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        Optional<Employee> emp = getAuthenticatedEmployee(authentication);
        if (emp.isPresent()) {
            notificationService.markAllAsRead(emp.get().getId());
        }
        return ResponseEntity.ok().build();
    }
}

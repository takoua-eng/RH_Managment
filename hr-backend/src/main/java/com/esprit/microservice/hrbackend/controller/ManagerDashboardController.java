package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.ManagerDashboardDTO;
import com.esprit.microservice.hrbackend.dto.ManagerDashboardStatsDTO;
import com.esprit.microservice.hrbackend.service.ManagerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/manager/dashboard")
@RequiredArgsConstructor
public class ManagerDashboardController {

    private final ManagerDashboardService managerDashboardService;

    @GetMapping({"/{managerId}", ""})
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<ManagerDashboardDTO> getManagerDashboard(
            @PathVariable(required = false) Long managerId,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT Token");
        }
        String keycloakId = jwt.getSubject();
        ManagerDashboardDTO dashboard = managerDashboardService.getManagerDashboard(keycloakId, managerId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<ManagerDashboardStatsDTO> getManagerDashboardStats(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT Token");
        }
        String keycloakId = jwt.getSubject();
        ManagerDashboardStatsDTO stats = managerDashboardService.getManagerDashboardStats(keycloakId);
        return ResponseEntity.ok(stats);
    }
}

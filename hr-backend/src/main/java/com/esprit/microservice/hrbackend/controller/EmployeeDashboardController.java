package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.EmployeeDashboardDTO;
import com.esprit.microservice.hrbackend.service.EmployeeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/employee/dashboard")
@RequiredArgsConstructor
public class EmployeeDashboardController {

    private final EmployeeDashboardService service;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        return new ResponseEntity<>(Map.of("message", String.valueOf(ex.getReason())), ex.getStatusCode());
    }

    /** Chaque utilisateur connecté (quel que soit son rôle) voit SES propres données. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public EmployeeDashboardDTO get(Authentication authentication) {
        return service.getDashboard(authentication);
    }
}
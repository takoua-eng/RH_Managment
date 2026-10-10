package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Profil de l'utilisateur connecté (GET /api/me), utilisé par le frontend après la connexion.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TestController {

    // Injection par constructeur (générée par Lombok) : dépendance obligatoire et non modifiable
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("roles", jwt.getClaim("realm_access"));

        Employee employee = currentUserService.getOrCreateCurrentEmployee(jwt);
        response.put("id", employee.getId());
        response.put("firstName", employee.getFirstName());
        response.put("lastName", employee.getLastName());
        response.put("phone", employee.getPhone());
        response.put("position", employee.getPosition());
        response.put("address", employee.getAddress());
        response.put("hireDate", employee.getHireDate());
        response.put("availableLeaveDays", employee.getAvailableLeaveDays());
        if (employee.getDepartment() != null) {
            response.put("department", employee.getDepartment().getName());
        }
        if (employee.getPhotoPath() != null && !employee.getPhotoPath().isEmpty()) {
            response.put("photoUrl", "/api/employees/" + employee.getId() + "/photo");
        }

        return ResponseEntity.ok(response);
    }
}
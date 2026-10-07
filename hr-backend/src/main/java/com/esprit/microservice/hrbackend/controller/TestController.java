package com.esprit.microservice.hrbackend.controller;


import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @GetMapping("/public/ping")
    public String publicEndpoint() {
        return "Accès public OK";
    }

    @GetMapping("/admin/ping")
    public String adminEndpoint() {
        return "Accès ADMIN OK";
    }

    @Autowired
    private com.esprit.microservice.hrbackend.service.CurrentUserService currentUserService;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");

        response.put("username", username);
        response.put("email", email);
        response.put("roles", jwt.getClaim("realm_access"));

        Employee employee = currentUserService.getOrCreateCurrentEmployee(jwt);
        if (employee != null) {
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
        }

        return ResponseEntity.ok(response);
    }
}
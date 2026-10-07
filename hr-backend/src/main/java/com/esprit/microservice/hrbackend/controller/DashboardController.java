package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.DashboardStatsDTO;
import com.esprit.microservice.hrbackend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats(
            @RequestParam(name = "months", defaultValue = "6", required = false) Integer months,
            @RequestParam(name = "department", required = false) String department
    ) {
        DashboardStatsDTO stats = dashboardService.getDashboardStats(months, department);
        return ResponseEntity.ok(stats);
    }
}

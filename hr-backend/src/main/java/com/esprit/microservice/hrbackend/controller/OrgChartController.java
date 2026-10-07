package com.esprit.microservice.hrbackend.controller;



import com.esprit.microservice.hrbackend.dto.OrgNodeDTO;
import com.esprit.microservice.hrbackend.service.OrgChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org-chart")
@RequiredArgsConstructor
public class OrgChartController {

    private final OrgChartService orgChartService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH','MANAGER','EMPLOYE')")
    public List<OrgNodeDTO> getOrgChart() {
        return orgChartService.buildOrgChart();
    }
}

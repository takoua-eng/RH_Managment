package com.esprit.microservice.hrbackend.service;


import com.esprit.microservice.hrbackend.dto.OrgNodeDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgChartService {

    private final EmployeeRepository employeeRepository;

    /** Construit l'organigramme complet à partir des employés sans manager (racines) */
    public List<OrgNodeDTO> buildOrgChart() {
        List<Employee> roots = employeeRepository.findByManagerIsNull();
        return roots.stream()
                .map(this::buildNode)
                .collect(Collectors.toList());
    }

    private OrgNodeDTO buildNode(Employee emp) {
        List<OrgNodeDTO> children = emp.getSubordinates().stream()
                .map(this::buildNode)
                .collect(Collectors.toList());

        return OrgNodeDTO.builder()
                .id(emp.getId())
                .name(emp.getFirstName() + " " + emp.getLastName())
                .position(emp.getPosition())
                .department(emp.getDepartment() != null ? emp.getDepartment().getName() : null)
                .photoUrl("/api/employees/" + emp.getId() + "/photo")
                .children(children)
                .build();
    }
}
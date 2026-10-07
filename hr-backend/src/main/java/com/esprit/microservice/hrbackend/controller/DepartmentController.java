package com.esprit.microservice.hrbackend.controller;



import com.esprit.microservice.hrbackend.dto.*;
import com.esprit.microservice.hrbackend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH','MANAGER')")
    public List<DepartmentResponseDTO> getAll() {
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH','MANAGER')")
    public DepartmentResponseDTO getById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<DepartmentResponseDTO> create(@RequestBody DepartmentRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public DepartmentResponseDTO update(@PathVariable Long id, @RequestBody DepartmentRequestDTO dto) {
        return departmentService.updateDepartment(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/employees")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public DepartmentResponseDTO assignEmployees(@PathVariable Long id, @RequestBody AssignEmployeesDTO dto) {
        return departmentService.assignEmployees(id, dto);
    }
    @GetMapping("/{id}/employees")
    @PreAuthorize("hasAnyRole('ADMIN','RH','MANAGER')")
    public List<EmployeeSummaryDTO> getEmployeesByDepartment(@PathVariable Long id) {
        return departmentService.getEmployeesOfDepartment(id);
    }

    @DeleteMapping("/{id}/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<Void> removeEmployee(@PathVariable Long id, @PathVariable Long employeeId) {
        departmentService.removeEmployeeFromDepartment(id, employeeId);
        return ResponseEntity.noContent().build();
    }
}

package com.esprit.microservice.hrbackend.service;



import com.esprit.microservice.hrbackend.dto.*;
import com.esprit.microservice.hrbackend.entity.Department;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.exception.*;
import com.esprit.microservice.hrbackend.repository.DepartmentRepository;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public DepartmentResponseDTO getDepartmentById(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
        return toResponseDTO(dept);
    }

    public DepartmentResponseDTO createDepartment(DepartmentRequestDTO dto) {
        if (departmentRepository.existsByName(dto.getName())) {
            throw new DepartmentAlreadyExistsException(dto.getName());
        }

        Department dept = Department.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .budget(dto.getBudget())
                .build();

        if (dto.getManagerId() != null) {
            Employee manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Employé manager introuvable"));
            dept.setManager(manager);
        }

        Department saved = departmentRepository.save(dept);
        return toResponseDTO(saved);
    }

    public DepartmentResponseDTO updateDepartment(Long id, DepartmentRequestDTO dto) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));

        dept.setName(dto.getName());
        dept.setDescription(dto.getDescription());
        dept.setLocation(dto.getLocation());
        dept.setBudget(dto.getBudget());

        if (dto.getManagerId() != null) {
            Employee manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Employé manager introuvable"));
            dept.setManager(manager);
        } else {
            dept.setManager(null);
        }

        Department saved = departmentRepository.save(dept);
        return toResponseDTO(saved);
    }

    public void deleteDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));

        // Détache les employés avant suppression (pour ne pas les supprimer en cascade)
        dept.getEmployees().forEach(emp -> emp.setDepartment(null));
        employeeRepository.saveAll(dept.getEmployees());

        departmentRepository.delete(dept);
    }

    /** Affecte une liste d'employés à un département */
    public DepartmentResponseDTO assignEmployees(Long departmentId, AssignEmployeesDTO dto) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        List<Employee> employees = employeeRepository.findAllById(dto.getEmployeeIds());
        employees.forEach(emp -> emp.setDepartment(dept));
        employeeRepository.saveAll(employees);

        Department refreshed = departmentRepository.findById(departmentId).orElseThrow();
        return toResponseDTO(refreshed);
    }

    /** Retire un employé de son département */
    public void removeEmployeeFromDepartment(Long departmentId, Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employé introuvable"));

        if (emp.getDepartment() == null || !emp.getDepartment().getId().equals(departmentId)) {
            throw new RuntimeException("Cet employé n'appartient pas à ce département");
        }

        emp.setDepartment(null);
        employeeRepository.save(emp);
    }

    private DepartmentResponseDTO toResponseDTO(Department dept) {
        EmployeeSummaryDTO managerDto = null;
        if (dept.getManager() != null) {
            Employee m = dept.getManager();
            managerDto = EmployeeSummaryDTO.builder()
                    .id(m.getId())
                    .firstName(m.getFirstName())
                    .lastName(m.getLastName())
                    .position(m.getPosition())
                    .photoUrl("/api/employees/" + m.getId() + "/photo")
                    .build();
        }

        return DepartmentResponseDTO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .manager(managerDto)
                .employeeCount(dept.getEmployees().size())
                .location(dept.getLocation())
                .budget(dept.getBudget())
                .build();
    }

    public List<EmployeeSummaryDTO> getEmployeesOfDepartment(Long departmentId) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        return dept.getEmployees().stream()
                .map(emp -> EmployeeSummaryDTO.builder()
                        .id(emp.getId())
                        .firstName(emp.getFirstName())
                        .lastName(emp.getLastName())
                        .position(emp.getPosition())
                        .photoUrl("/api/employees/" + emp.getId() + "/photo")
                        .build())
                .collect(Collectors.toList());
    }
}
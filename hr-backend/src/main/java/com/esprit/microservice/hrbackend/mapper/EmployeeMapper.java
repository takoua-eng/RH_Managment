package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.EmployeeRequestDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.entity.Employee;

public class EmployeeMapper {

    public static EmployeeResponseDTO toResponseDTO(Employee employee) {
        if (employee == null) {
            return null;
        }

        String photoUrl = null;
        if (employee.getPhotoPath() != null && !employee.getPhotoPath().isEmpty()) {
            photoUrl = "http://localhost:8087/api/employees/" + employee.getId() + "/photo";
        }

        // Infos du manager direct
        Long   managerId   = null;
        String managerName = null;
        if (employee.getManager() != null) {
            managerId   = employee.getManager().getId();
            managerName = employee.getManager().getFirstName()
                        + " " + employee.getManager().getLastName();
        }

        return EmployeeResponseDTO.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .position(employee.getPosition())
                .department(
                employee.getDepartment() != null
                        ? employee.getDepartment().getName()
                        : null
        )
                .hireDate(employee.getHireDate())
                .status(employee.getStatus())
                .role(employee.getRole())
                .keycloakId(employee.getKeycloakId())
                .photoContentType(employee.getPhotoContentType())
                .photoUrl(photoUrl)
                .salary(employee.getSalary())
                .availableLeaveDays(employee.getAvailableLeaveDays())
                .address(employee.getAddress())
                .managerId(managerId)
                .managerName(managerName)
                .build();
    }

    public static Employee toEntity(EmployeeRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        return Employee.builder()
                .firstName(requestDTO.getFirstName())
                .lastName(requestDTO.getLastName())
                .email(requestDTO.getEmail())
                .phone(requestDTO.getPhone())
                .position(requestDTO.getPosition())
                .hireDate(requestDTO.getHireDate())
                .salary(requestDTO.getSalary())
                .availableLeaveDays(requestDTO.getAvailableLeaveDays() != null ? requestDTO.getAvailableLeaveDays() : 25)
                .address(requestDTO.getAddress())
                .role(requestDTO.getRole())
                .build();
    }

    public static void updateEntityFromDTO(EmployeeRequestDTO requestDTO, Employee employee) {
        if (requestDTO == null || employee == null) {
            return;
        }

        employee.setFirstName(requestDTO.getFirstName());
        employee.setLastName(requestDTO.getLastName());
        employee.setEmail(requestDTO.getEmail());
        employee.setPhone(requestDTO.getPhone());
        employee.setPosition(requestDTO.getPosition());
        employee.setHireDate(requestDTO.getHireDate());
        employee.setSalary(requestDTO.getSalary());
        if (requestDTO.getAvailableLeaveDays() != null) {
            employee.setAvailableLeaveDays(requestDTO.getAvailableLeaveDays());
        }
        employee.setAddress(requestDTO.getAddress());
        if (requestDTO.getRole() != null) {
            employee.setRole(requestDTO.getRole());
        }
    }
}

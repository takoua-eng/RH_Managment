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
        if (employee.getPhoto() != null && employee.getPhoto().length > 0) {
            photoUrl = "/api/employees/" + employee.getId() + "/photo";
        }

        return EmployeeResponseDTO.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .position(employee.getPosition())
                .department(employee.getDepartment())
                .hireDate(employee.getHireDate())
                .status(employee.getStatus())
                .keycloakId(employee.getKeycloakId())
                .photoContentType(employee.getPhotoContentType())
                .photoUrl(photoUrl)
                .salary(employee.getSalary())
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
                .department(requestDTO.getDepartment())
                .hireDate(requestDTO.getHireDate())
                .salary(requestDTO.getSalary())
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
        employee.setDepartment(requestDTO.getDepartment());
        employee.setHireDate(requestDTO.getHireDate());
        employee.setSalary(requestDTO.getSalary());
    }
}

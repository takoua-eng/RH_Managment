package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.Status;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String position;
    private String department;
    private LocalDate hireDate;
    private Status status;
    private String keycloakId;
    private String photoContentType;
    private String photoUrl;
    private Double salary;
}

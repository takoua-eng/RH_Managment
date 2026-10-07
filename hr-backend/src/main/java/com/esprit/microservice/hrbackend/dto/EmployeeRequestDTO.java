package com.esprit.microservice.hrbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRequestDTO {
    
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'adresse email est obligatoire")
    @Email(message = "Adresse email invalide (ex: nom@domaine.com)")
    private String email;

    @Pattern(regexp = "^[+]?[0-9\\s-]{8,20}$", message = "Le numéro doit contenir entre 8 et 20 chiffres")
    private String phone;

    private String position;

    private Long departmentId;
    private String department;

    private LocalDate hireDate;

    private Double salary;

    private Integer availableLeaveDays;
    private String address;
    private Long managerId;
    private com.esprit.microservice.hrbackend.entity.Role role;
}

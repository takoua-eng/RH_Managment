package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.Role;
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
    private Role role;
    private String keycloakId;
    private String photoContentType;
    private String photoUrl;
    private Double salary;
    private Integer availableLeaveDays;
    private String address;

    /** ID du manager direct de cet employé (null si non affecté) */
    private Long managerId;

    /** Nom complet du manager (prénom + nom) pour affichage dans l'UI */
    private String managerName;

    private boolean onLeaveToday;          // calculé : congé approuvé couvrant aujourd'hui
    private LocalDate leaveReturnDate;     // jour du retour (lendemain de la fin du congé)
}

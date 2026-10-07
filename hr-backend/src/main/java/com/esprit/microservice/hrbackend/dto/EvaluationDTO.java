package com.esprit.microservice.hrbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationDTO {

    private Long id;

    @NotNull(message = "L'ID de l'employé est requis")
    private Long employeeId;
    private String employeeName;

    private Long managerId;
    private String managerName;

    @NotNull(message = "La date d'évaluation est requise")
    private LocalDate date;

    @Min(0) @Max(10)
    private int communication;

    @Min(0) @Max(10)
    private int leadership;

    @Min(0) @Max(10)
    private int technical;

    @Min(0) @Max(10)
    private int teamwork;

    @Min(0) @Max(10)
    private int productivity;

    private String comments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

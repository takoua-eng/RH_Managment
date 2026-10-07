package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.InterviewType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InterviewRequestDTO {

    @NotNull(message = "Le candidat est obligatoire")
    private Long candidateId;

    @NotNull(message = "La date d'entretien est obligatoire")
    private LocalDate interviewDate;

    @NotNull(message = "L'heure d'entretien est obligatoire")
    private String interviewTime;

    @NotNull(message = "La durée est obligatoire")
    private Integer durationMinutes;

    @NotNull(message = "Le type d'entretien est obligatoire")
    private InterviewType type;

    private String locationOrLink;

    private String comment;
}
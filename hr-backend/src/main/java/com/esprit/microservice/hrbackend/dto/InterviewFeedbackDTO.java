package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.ManagerAvis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewFeedbackDTO {

    @NotNull(message = "La recommandation est obligatoire")
    private ManagerAvis recommendation;

    @NotNull(message = "La note est obligatoire")
    @Min(value = 1, message = "La note minimale est 1")
    @Max(value = 5, message = "La note maximale est 5")
    private Integer rating;

    @Size(max = 5000, message = "Le commentaire ne peut pas dépasser 5000 caractères")
    private String comment;
}

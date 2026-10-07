package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.InterviewStatus;
import com.esprit.microservice.hrbackend.entity.InterviewType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.esprit.microservice.hrbackend.entity.ManagerAvis;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewResponseDTO {
    private Long id;
    private Long candidateId;
    private String candidateFirstName;
    private String candidateLastName;
    private String candidateEmail;
    private Long jobOfferId;
    private String jobOfferTitle;
    private String department;
    private Long managerId;
    private String managerName;
    private LocalDate interviewDate;
    private String interviewTime;
    private Integer durationMinutes;
    private InterviewType type;
    private String locationOrLink;
    private String comment;
    private InterviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer modificationCount;
    private Boolean emailSent;
    private LocalDateTime emailSentAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    // ===== Questions IA =====
    @JsonRawValue
    private String aiQuestions;              // envoyé au frontend comme un vrai objet JSON

    private StatutAnalyse aiQuestionsStatus; // EN_ATTENTE, TERMINEE, ERREUR
    private String aiQuestionsError;

    // ===== Notes et Avis du Manager =====
    @JsonRawValue
    private String interviewNotes;            // envoyé au frontend comme un vrai objet JSON

    private ManagerAvis managerRecommendation;
    private Integer managerRating;
    private String managerFeedback;
    private LocalDateTime feedbackSubmittedAt;
}

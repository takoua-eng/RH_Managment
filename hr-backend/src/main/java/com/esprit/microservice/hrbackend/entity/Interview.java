package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews")
@DynamicUpdate
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate interviewDate;

    @Column(nullable = false)
    private String interviewTime; // e.g. "10:30"

    @Column(nullable = false)
    private Integer durationMinutes; // e.g. 45

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewType type;

    private String locationOrLink;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.PLANIFIE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private Employee manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    private Integer modificationCount = 0;

    @Builder.Default
    private Boolean emailSent = false;

    private LocalDateTime emailSentAt;

    private LocalDateTime cancelledAt;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    // ===== Questions d'entretien générées par l'IA =====
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_questions", columnDefinition = "jsonb")
    @ToString.Exclude
    private String aiQuestions;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_questions_status", length = 20)
    private StatutAnalyse aiQuestionsStatus;      // EN_ATTENTE, TERMINEE, ERREUR

    @Column(name = "ai_questions_error", columnDefinition = "TEXT")
    private String aiQuestionsError;

    @Column(name = "ai_questions_generated_at")
    private LocalDateTime aiQuestionsGeneratedAt;

    // ===== Notes et Avis du Manager =====
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interview_notes", columnDefinition = "jsonb")
    @ToString.Exclude
    private String interviewNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_recommendation")
    private ManagerAvis managerRecommendation;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_feedback", columnDefinition = "TEXT")
    private String managerFeedback;

    @Column(name = "feedback_submitted_at")
    private LocalDateTime feedbackSubmittedAt;
}

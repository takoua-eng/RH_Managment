package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "candidates")
@DynamicUpdate
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String address;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @CreationTimestamp
    private LocalDate applicationDate;

    @Column(name = "transmission_date")
    private LocalDate transmissionDate;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CandidateStatus status = CandidateStatus.NOUVELLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_manager_id")
    private Employee assignedManager;

    // CV
    private String cvFileName;
    private String cvContentType;
    @Column(name = "cv_path")
    private String cvPath;

    // Motivation Letter
    private String motivationLetterFileName;
    private String motivationLetterContentType;
    @Column(name = "motivation_letter_path")
    private String motivationLetterPath;

    // ===== Analyse IA =====
    @Column(name = "ai_score")
    private Double aiScore;

    @Column(name = "ai_recommendation", length = 20)
    private String aiRecommendation;            // COMPATIBLE, A_EXAMINER, NON_COMPATIBLE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_analysis", columnDefinition = "jsonb")
    @ToString.Exclude
    private String aiAnalysis;                  // profil, features, points forts/faibles

    @Column(name = "cv_text", columnDefinition = "TEXT")
    @ToString.Exclude
    private String cvText;

    @Column(name = "motivation_letter_text", columnDefinition = "TEXT")
    @ToString.Exclude
    private String motivationLetterText;

    @Column(name = "ai_analysis_date")
    private LocalDateTime aiAnalysisDate;

    @Column(name = "ai_error", columnDefinition = "TEXT")
    private String aiError;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", length = 20)
    @Builder.Default
    private StatutAnalyse aiStatus = StatutAnalyse.EN_ATTENTE;
}

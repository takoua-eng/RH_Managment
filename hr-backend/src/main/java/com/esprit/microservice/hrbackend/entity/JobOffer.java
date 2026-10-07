package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "job_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private String department;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "experience_level")
    private String experienceLevel;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    private String location;

    @Column(name = "salary_range")
    private String salaryRange;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "number_of_positions")
    @Builder.Default
    private Integer numberOfPositions = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobOfferStatus status = JobOfferStatus.DRAFT;

    /** Niveau d'études minimum : 0 = aucun, 1 = bac, 2 = bac+2, 3 = bac+3, 4 = bac+5, 5 = doctorat */
    @Column(name = "min_education_level")
    @Builder.Default
    private Integer minEducationLevel = 0;

    /** Langues demandées, séparées par des virgules : "Français, Anglais" */
    @Column(name = "required_languages", columnDefinition = "TEXT")
    private String requiredLanguages;
}

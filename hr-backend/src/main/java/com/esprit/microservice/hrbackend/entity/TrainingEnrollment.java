package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "training_enrollments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "training_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    private LocalDate enrollmentDate;

    private String status; // "À venir", "En cours", "Terminée", "Annulée"

    @Builder.Default
    private Integer progression = 0;

    @Column(name = "certificate_path")
    private String certificatePath;
}

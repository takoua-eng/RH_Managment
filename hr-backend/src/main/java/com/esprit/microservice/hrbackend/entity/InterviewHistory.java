package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long interviewId;

    private LocalDate previousInterviewDate;
    private String previousInterviewTime;
    private Integer previousDurationMinutes;

    @Enumerated(EnumType.STRING)
    private InterviewType previousType;

    private String previousLocationOrLink;

    @Column(columnDefinition = "TEXT")
    private String previousComment;

    @CreationTimestamp
    private LocalDateTime modifiedAt;

    private String modifiedBy;
}

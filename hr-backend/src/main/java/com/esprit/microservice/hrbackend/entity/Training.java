package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "trainings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    private String trainer;

    private LocalDate startDate;

    private String duration;

    private String location;

    private String category;
    
    private String level;
    
    private LocalDate endDate;
    
    private Integer availableSeats;
    
    private String mode;
    
    @Column(length = 1000)
    private String objectives;
    
    private String status;

    @Column(length = 2000)
    private String syllabus;
}

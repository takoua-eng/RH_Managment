package com.esprit.microservice.hrbackend.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingDTO {
    private Long id;
    private String title;
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
    private String objectives;
    private String status;
    private String syllabus;
}

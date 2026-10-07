package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.JobOfferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobOfferRequestDTO {
    private String title;
    private String description;
    private String department;
    private String contractType;
    private String experienceLevel;
    private String requiredSkills;
    private String location;
    private String salaryRange;
    private LocalDate applicationDeadline;
    private Integer numberOfPositions;
    private JobOfferStatus status;
}

package com.esprit.microservice.hrbackend.dto;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingEnrollmentDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeePosition;
    private TrainingDTO training;
    private LocalDate enrollmentDate;
    private String status;
    private Integer progression;
    private boolean hasCertificate;
}

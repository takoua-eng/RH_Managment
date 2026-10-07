package com.esprit.microservice.hrbackend.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveCalendarDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private List<String> dates;
    private String type; // PAID, SICK, RTT
    private LocalDate startDate;
    private LocalDate endDate;
    private long daysCount;
    private String status;
    private String reason;
}


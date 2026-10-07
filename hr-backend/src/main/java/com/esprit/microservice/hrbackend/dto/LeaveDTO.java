package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveDTO {
    private Long id;
    
    @NotNull(message = "Employee ID is mandatory")
    private Long employeeId;
    
    private String employeeName;
    
    @NotNull(message = "Start date is mandatory")
    private LocalDate startDate;
    
    @NotNull(message = "End date is mandatory")
    private LocalDate endDate;
    
    @NotNull(message = "Leave type is mandatory")
    private LeaveType type;
    
    private LeaveStatus status;
    private String reason;
    private LocalDateTime createdAt;
}

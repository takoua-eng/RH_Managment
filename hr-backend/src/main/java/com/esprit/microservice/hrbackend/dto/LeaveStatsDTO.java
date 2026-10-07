package com.esprit.microservice.hrbackend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveStatsDTO {
    private long pendingCount;
    private long absentToday;
    private long approvedThisMonth;
    private double presenceRate;
}

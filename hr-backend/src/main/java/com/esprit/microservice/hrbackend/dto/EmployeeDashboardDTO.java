package com.esprit.microservice.hrbackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Synthèse du tableau de bord de l'employé connecté. */
public record EmployeeDashboardDTO(
        Profile profile,
        LeaveStatusInfo leaveStatus,
        List<LeaveItem> recentLeaves,
        ManagerInfo manager,
        List<TeamAbsence> teamAbsences,
        TrainingsInfo trainings,
        List<MonthLeave> monthLeaves,
        List<NotificationItem> notifications
) {
    public record Profile(Long id, String firstName, String lastName, String position,
                          String departmentName, LocalDate hireDate, String seniorityText,
                          Integer availableLeaveDays) {}

    public record LeaveStatusInfo(boolean onLeaveToday, LocalDate currentLeaveEnd,
                                  LeaveItem nextApprovedLeave, Long daysUntilNextLeave,
                                  long pendingRequests, long daysTakenThisYear) {}

    public record LeaveItem(Long id, String type, LocalDate startDate, LocalDate endDate,
                            long daysCount, String status, String reason,
                            String decisionComment, LocalDateTime decidedAt, String decidedByName) {}

    public record ManagerInfo(Long id, String firstName, String lastName,
                              String position, String email) {}

    public record TeamAbsence(String name, LocalDate startDate, LocalDate endDate, boolean today) {}

    public record TrainingItem(String title, Integer progression) {}

    public record TrainingsInfo(List<TrainingItem> inProgress, long completedCount) {}

    public record MonthLeave(LocalDate startDate, LocalDate endDate, String status) {}

    public record NotificationItem(Long id, String text, String type, boolean unread,
                                   LocalDateTime createdAt) {}
}
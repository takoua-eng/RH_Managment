package com.esprit.microservice.hrbackend.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerDashboardDTO {

    // =========================================================
    // KPI INDICATEURS
    // =========================================================

    /** Nombre total d'employés dans l'équipe directe du manager */
    private int totalEmployees;

    /** Nombre de congés en attente (PENDING) pour l'équipe */
    private int pendingLeaves;

    /** Nombre d'employés inscrits à au moins une formation */
    private int enrolledEmployeesCount;

    /** Nombre de formations en cours */
    private int activeTrainings;

    /** Nombre de formations terminées */
    private int completedTrainings;

    /** Progression moyenne de l'équipe (en pourcentage) */
    private double averageProgression;

    /** Formations les plus suivies (titre -> nombre d'inscrits) */
    private java.util.Map<String, Long> topTrainings;

    /** Nombre d'employés actifs dans l'équipe */
    private int activeEmployees;

    // =========================================================
    // DONNÉES DÉTAILLÉES
    // =========================================================

    /** Résumé des membres de l'équipe */
    private List<EmployeeSummaryDTO> teamMembers;

    /** Notifications récentes contextuelles au manager */
    private List<ManagerNotificationDTO> recentNotifications;
}

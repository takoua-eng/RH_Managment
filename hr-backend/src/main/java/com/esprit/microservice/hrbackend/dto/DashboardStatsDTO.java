package com.esprit.microservice.hrbackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardStatsDTO(
    KpiStats kpis,
    List<MonthlyTrend> monthlyTrend,
    FunnelStats funnel,
    AiRecommendationStats aiRecommendations,
    List<ScoreDistributionBucket> aiScoreDistribution,
    List<MissingSkillCount> topMissingSkills,
    AiManagerAgreementStats aiManagerAgreement,
    ActionsRequiredStats actionsRequired,
    List<UpcomingInterviewDto> upcomingInterviews,
    List<TopOfferDto> topOffers,
    List<DepartmentLeavesDto> leavesByDepartment,
    AiServiceStats aiService
) {

    public record KpiStats(
        long totalEmployees,
        long pendingLeaves,
        long activeJobOffers,
        long applicationsThisMonth,
        long applicationsLastMonth,
        long interviewsThisWeek,
        long pendingManagerFeedbacks,
        Double averageAiScore,
        Double averageTimeToDecisionDays
    ) {}

    public record MonthlyTrend(
        String month,        // "2026-05"
        String label,        // "mai 26"
        long applications,
        long hired
    ) {}

    public record FunnelStats(
        long received,
        long aiAnalyzed,
        long aiCompatible,
        long transmittedToManager,
        long interviewScheduled,
        long interviewEvaluated,
        long accepted
    ) {}

    public record AiRecommendationStats(
        long compatible,
        long aExaminer,
        long nonCompatible,
        long erreur,
        long enAttente
    ) {}

    public record ScoreDistributionBucket(
        String range,       // "0-10%", "10-20%", etc.
        long count
    ) {}

    public record MissingSkillCount(
        String skill,
        long count
    ) {}

    public record AiManagerAgreementStats(
        AgreementMatrix matrix,
        Double agreementRate,
        long totalPairs
    ) {
        public record AgreementMatrix(
            long compatibleFavorable,
            long compatibleReserve,
            long compatibleDefavorable,
            long aExaminerFavorable,
            long aExaminerReserve,
            long aExaminerDefavorable,
            long nonCompatibleFavorable,
            long nonCompatibleReserve,
            long nonCompatibleDefavorable
        ) {}
    }

    public record ActionsRequiredStats(
        List<UnprocessedApplicationDto> unprocessedApplications,
        long totalUnprocessedCount,
        List<AiErrorDto> aiErrors,
        long totalAiErrorsCount,
        List<MissingFeedbackDto> missingFeedbacks,
        long totalMissingFeedbacksCount,
        List<ExpiringOfferDto> expiringOffers,
        long totalExpiringOffersCount
    ) {
        public record UnprocessedApplicationDto(
            Long id,
            String name,
            String jobTitle,
            long daysPending
        ) {}

        public record AiErrorDto(
            Long id,
            String name,
            String jobTitle,
            String aiError
        ) {}

        public record MissingFeedbackDto(
            Long interviewId,
            String candidateName,
            String managerName,
            LocalDate interviewDate
        ) {}

        public record ExpiringOfferDto(
            Long offerId,
            String title,
            LocalDate applicationDeadline
        ) {}
    }

    public record UpcomingInterviewDto(
        Long id,
        LocalDate interviewDate,
        String interviewTime,
        String candidateName,
        String jobTitle,
        String managerName,
        String aiQuestionsStatus
    ) {}

    public record TopOfferDto(
        Long id,
        String title,
        String department,
        long applicationsCount,
        Double averageAiScore,
        String bestCandidateName,
        Double bestCandidateScore
    ) {}

    public record DepartmentLeavesDto(
        String departmentName,
        long leavesCount
    ) {}

    public record AiServiceStats(
        boolean online,
        String methode,
        LocalDateTime checkedAt,
        LocalDateTime lastAnalysisAt,
        long analysesLast30Days
    ) {}
}

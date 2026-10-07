package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.InterviewType;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;

import java.time.LocalDate;
import java.util.List;

public record ManagerDashboardStatsDTO(
    Kpis kpis,
    InterviewDetailsDto nextInterview,
    List<InterviewDetailsDto> upcomingInterviews,
    List<CandidateToProcessDto> candidatesToProcess,
    List<PendingFeedbackDto> pendingFeedbacks,
    MyEvaluationsStats myEvaluations,
    TeamAbsencesStats teamAbsences
) {

    public record Kpis(
        long candidatesToProcess,
        long interviewsThisWeek,
        long pendingFeedbacks,
        Double averageAiScoreOfTransmitted
    ) {}

    public record InterviewDetailsDto(
        Long interviewId,
        Long candidateId,
        String candidateName,
        String jobTitle,
        LocalDate date,
        String time,
        Integer durationMinutes,
        InterviewType type,
        String locationOrLink,
        StatutAnalyse aiQuestionsStatus,
        Double candidateAiScore,
        String candidateAiRecommendation
    ) {}

    public record CandidateToProcessDto(
        Long candidateId,
        String name,
        String jobTitle,
        LocalDate applicationDate,
        Double aiScore,
        String aiRecommendation,
        StatutAnalyse aiStatus
    ) {}

    public record PendingFeedbackDto(
        Long interviewId,
        String candidateName,
        String jobTitle,
        LocalDate date,
        long daysSince
    ) {}

    public record MyEvaluationsStats(
        long favorable,
        long reserve,
        long defavorable,
        long totalEvaluated,
        Double averageRating,
        Double agreementWithAi
    ) {}

    public record TeamAbsencesStats(
        List<AbsentTodayDto> absentToday,
        List<UpcomingLeaveDto> upcomingLeaves
    ) {
        public record AbsentTodayDto(
            String name,
            LocalDate returnDate
        ) {}

        public record UpcomingLeaveDto(
            String name,
            LocalDate startDate,
            LocalDate endDate
        ) {}
    }
}

package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.DashboardStatsDTO;
import com.esprit.microservice.hrbackend.dto.DashboardStatsDTO.*;
import com.esprit.microservice.hrbackend.dto.DashboardStatsDTO.ActionsRequiredStats.*;
import com.esprit.microservice.hrbackend.dto.DashboardStatsDTO.AiManagerAgreementStats.*;
import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistiques du tableau de bord admin.
 *
 * Pas de @Transactional ici : chaque requête s'exécute dans sa propre transaction.
 * Ainsi, une requête SQL en erreur ne bloque pas toutes les suivantes
 * ("la transaction est annulée…"). Le chargement des relations LAZY reste possible
 * grâce à spring.jpa.open-in-view, actif par défaut.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final JobOfferRepository jobOfferRepository;
    private final LeaveRepository leaveRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DashboardRepository dashboardRepository;
    private final RestClient aiRestClient;

    public DashboardStatsDTO getDashboardStats(Integer monthsParam, String departmentParam) {
        int months = (monthsParam == null || monthsParam < 1) ? 6 : Math.min(monthsParam, 12);
        String deptFilter = (departmentParam != null && !departmentParam.isBlank()) ? departmentParam.trim() : null;

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1).minusMonths(months - 1);

        // 1. KPIs
        KpiStats kpis = buildKpiStats(today, startDate, deptFilter);

        // 2. Monthly Trend
        List<MonthlyTrend> monthlyTrend = buildMonthlyTrend(today, months, deptFilter);

        // 3. Funnel
        FunnelStats funnel = buildFunnelStats(startDate, deptFilter);

        // 4. AI Recommendations
        AiRecommendationStats aiRecommendations = buildAiRecommendationStats(startDate, deptFilter);

        // 5. AI Score Distribution
        List<ScoreDistributionBucket> aiScoreDistribution = buildAiScoreDistribution(startDate, deptFilter);

        // 6. Top Missing Skills
        List<MissingSkillCount> topMissingSkills = buildTopMissingSkills(startDate, deptFilter);

        // 7. AI Manager Agreement Matrix
        AiManagerAgreementStats aiManagerAgreement = buildAiManagerAgreement(startDate, deptFilter);

        // 8. Actions Required
        ActionsRequiredStats actionsRequired = buildActionsRequired(today, deptFilter);

        // 10. Upcoming Interviews
        List<UpcomingInterviewDto> upcomingInterviews = buildUpcomingInterviews(today, deptFilter);

        // 11. Top Offers
        List<TopOfferDto> topOffers = buildTopOffers(startDate, deptFilter);

        // 12. Leaves by Department
        List<DepartmentLeavesDto> leavesByDepartment = buildLeavesByDepartment();

        // 13. AI Service Health & Stats
        AiServiceStats aiService = buildAiServiceStats();

        return new DashboardStatsDTO(
                kpis,
                monthlyTrend,
                funnel,
                aiRecommendations,
                aiScoreDistribution,
                topMissingSkills,
                aiManagerAgreement,
                actionsRequired,
                upcomingInterviews,
                topOffers,
                leavesByDepartment,
                aiService
        );
    }

    private KpiStats buildKpiStats(LocalDate today, LocalDate startDate, String deptFilter) {
        long totalEmployees = employeeRepository.findAll().stream()
                .filter(e -> deptFilter == null || (e.getDepartment() != null && deptFilter.equalsIgnoreCase(e.getDepartment().getName())))
                .count();

        long pendingLeaves = leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.PENDING)
                .filter(l -> {
                    if (deptFilter == null) return true;
                    Employee emp = employeeRepository.findById(l.getEmployeeId()).orElse(null);
                    return emp != null && emp.getDepartment() != null && deptFilter.equalsIgnoreCase(emp.getDepartment().getName());
                })
                .count();

        long activeJobOffers = jobOfferRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobOfferStatus.PUBLISHED)
                .filter(j -> j.getApplicationDeadline() == null || !j.getApplicationDeadline().isBefore(today))
                .filter(j -> deptFilter == null || deptFilter.equalsIgnoreCase(j.getDepartment()))
                .count();

        LocalDate firstDayThisMonth = today.withDayOfMonth(1);
        LocalDate firstDayLastMonth = firstDayThisMonth.minusMonths(1);
        LocalDate lastDayLastMonth = firstDayThisMonth.minusDays(1);

        List<Candidate> allCandidates = candidateRepository.findAll();

        long appsThisMonth = allCandidates.stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(firstDayThisMonth) && !c.getApplicationDate().isAfter(today))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .count();

        long appsLastMonth = allCandidates.stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(firstDayLastMonth) && !c.getApplicationDate().isAfter(lastDayLastMonth))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .count();

        LocalDate endOfWeek = today.plusDays(7);
        List<Interview> allInterviews = interviewRepository.findAll();

        long interviewsThisWeek = allInterviews.stream()
                .filter(i -> i.getStatus() == InterviewStatus.PLANIFIE)
                .filter(i -> i.getInterviewDate() != null && !i.getInterviewDate().isBefore(today) && !i.getInterviewDate().isAfter(endOfWeek))
                .filter(i -> deptFilter == null || (i.getJobOffer() != null && deptFilter.equalsIgnoreCase(i.getJobOffer().getDepartment())))
                .count();

        long pendingFeedbacks = allInterviews.stream()
                .filter(i -> i.getInterviewDate() != null && i.getInterviewDate().isBefore(today))
                .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                .filter(i -> i.getManagerRecommendation() == null)
                .filter(i -> deptFilter == null || (i.getJobOffer() != null && deptFilter.equalsIgnoreCase(i.getJobOffer().getDepartment())))
                .count();

        OptionalDouble avgScoreOpt = allCandidates.stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> c.getAiScore() != null)
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .mapToDouble(Candidate::getAiScore)
                .average();
        Double avgAiScore = avgScoreOpt.isPresent() ? Math.round(avgScoreOpt.getAsDouble() * 100.0) / 100.0 : null;

        Double avgDecisionDays = null;
        try {
            avgDecisionDays = dashboardRepository.findAverageTimeToDecisionDays(startDate, deptFilter);
            if (avgDecisionDays != null) {
                avgDecisionDays = Math.round(avgDecisionDays * 10.0) / 10.0;
            }
        } catch (Exception e) {
            log.warn("Error calculating averageTimeToDecisionDays: {}", e.getMessage());
        }

        return new KpiStats(
                totalEmployees,
                pendingLeaves,
                activeJobOffers,
                appsThisMonth,
                appsLastMonth,
                interviewsThisWeek,
                pendingFeedbacks,
                avgAiScore,
                avgDecisionDays
        );
    }

    private List<MonthlyTrend> buildMonthlyTrend(LocalDate today, int months, String deptFilter) {
        List<MonthlyTrend> list = new ArrayList<>();
        YearMonth currentYm = YearMonth.from(today);
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.FRENCH);

        Map<String, Long> appsMap = new HashMap<>();
        Map<String, Long> hiredMap = new HashMap<>();

        LocalDate startDate = today.withDayOfMonth(1).minusMonths(months - 1);

        try {
            List<Object[]> appsRes = dashboardRepository.findMonthlyApplicationsCount(startDate, deptFilter);
            for (Object[] row : appsRes) {
                if (row[0] != null && row[1] != null) {
                    appsMap.put(row[0].toString(), ((Number) row[1]).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching monthly applications: {}", e.getMessage());
        }

        try {
            List<Object[]> hiredRes = dashboardRepository.findMonthlyHiredCount(startDate, deptFilter);
            for (Object[] row : hiredRes) {
                if (row[0] != null && row[1] != null) {
                    hiredMap.put(row[0].toString(), ((Number) row[1]).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching monthly hired: {}", e.getMessage());
        }

        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = currentYm.minusMonths(i);
            String ymStr = ym.toString();
            String label = ym.atDay(1).format(labelFormatter).toLowerCase();

            long apps = appsMap.getOrDefault(ymStr, 0L);
            long hired = hiredMap.getOrDefault(ymStr, 0L);

            list.add(new MonthlyTrend(ymStr, label, apps, hired));
        }

        return list;
    }

    private FunnelStats buildFunnelStats(LocalDate startDate, String deptFilter) {
        List<Candidate> candidates = candidateRepository.findAll().stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .collect(Collectors.toList());

        List<Interview> interviews = interviewRepository.findAll();
        Set<Long> candidateIdsWithInterview = interviews.stream()
                .map(i -> i.getCandidate() != null ? i.getCandidate().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> candidateIdsWithEvaluatedInterview = interviews.stream()
                .filter(i -> i.getManagerRecommendation() != null)
                .map(i -> i.getCandidate() != null ? i.getCandidate().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Étape 1 Reçu : total candidatures de la période
        long received = candidates.size();

        // Étape 2 Analyse IA : candidatures avec aiStatus = TERMINEE
        long aiAnalyzed = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.TERMINEE)
                .count();

        // Étape 3 Compatible IA : candidatures avec aiRecommendation = COMPATIBLE
        long aiCompatible = candidates.stream()
                .filter(c -> "COMPATIBLE".equalsIgnoreCase(c.getAiRecommendation()))
                .count();

        // Étape 4 Transmis au manager : date de transmission renseignée ou statut au-delà de NOUVELLE
        long transmitted = candidates.stream()
                .filter(c -> c.getTransmissionDate() != null || (c.getStatus() != null && c.getStatus() != CandidateStatus.NOUVELLE))
                .count();

        // Étape 5 Entretien planifié : un entretien existe, ou statut entretien / décision
        long scheduled = candidates.stream()
                .filter(c -> candidateIdsWithInterview.contains(c.getId()) ||
                        (c.getStatus() != null && (
                                c.getStatus() == CandidateStatus.ENTRETIEN_PLANIFIE ||
                                        c.getStatus() == CandidateStatus.ENTRETIEN_TERMINE ||
                                        c.getStatus() == CandidateStatus.ACCEPTEE ||
                                        c.getStatus() == CandidateStatus.REFUSEE
                        )))
                .count();

        // Étape 6 Entretien évalué : avis du manager soumis, ou statut entretien terminé / décision
        long evaluated = candidates.stream()
                .filter(c -> candidateIdsWithEvaluatedInterview.contains(c.getId()) ||
                        (c.getStatus() != null && (
                                c.getStatus() == CandidateStatus.ENTRETIEN_TERMINE ||
                                        c.getStatus() == CandidateStatus.ACCEPTEE ||
                                        c.getStatus() == CandidateStatus.REFUSEE
                        )))
                .count();

        // Étape 7 Accepté : statut final ACCEPTEE
        long accepted = candidates.stream()
                .filter(c -> c.getStatus() == CandidateStatus.ACCEPTEE)
                .count();

        return new FunnelStats(received, aiAnalyzed, aiCompatible, transmitted, scheduled, evaluated, accepted);
    }

    private AiRecommendationStats buildAiRecommendationStats(LocalDate startDate, String deptFilter) {
        List<Candidate> candidates = candidateRepository.findAll().stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .collect(Collectors.toList());

        long compatible = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.TERMINEE && "COMPATIBLE".equalsIgnoreCase(c.getAiRecommendation()))
                .count();

        long aExaminer = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.TERMINEE && "A_EXAMINER".equalsIgnoreCase(c.getAiRecommendation()))
                .count();

        long nonCompatible = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.TERMINEE && "NON_COMPATIBLE".equalsIgnoreCase(c.getAiRecommendation()))
                .count();

        long erreur = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.ERREUR)
                .count();

        long enAttente = candidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.EN_ATTENTE)
                .count();

        return new AiRecommendationStats(compatible, aExaminer, nonCompatible, erreur, enAttente);
    }

    private List<ScoreDistributionBucket> buildAiScoreDistribution(LocalDate startDate, String deptFilter) {
        long[] buckets = new long[10];

        candidateRepository.findAll().stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> c.getAiStatus() == StatutAnalyse.TERMINEE && c.getAiScore() != null)
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .forEach(c -> {
                    double score = c.getAiScore();
                    int idx = (int) (score * 10);
                    if (idx < 0) idx = 0;
                    if (idx > 9) idx = 9;
                    buckets[idx]++;
                });

        List<ScoreDistributionBucket> list = new ArrayList<>();
        String[] ranges = {"0-10%", "10-20%", "20-30%", "30-40%", "40-50%", "50-60%", "60-70%", "70-80%", "80-90%", "90-100%"};
        for (int i = 0; i < 10; i++) {
            list.add(new ScoreDistributionBucket(ranges[i], buckets[i]));
        }
        return list;
    }

    private List<MissingSkillCount> buildTopMissingSkills(LocalDate startDate, String deptFilter) {
        List<MissingSkillCount> list = new ArrayList<>();
        try {
            List<Object[]> rows = dashboardRepository.findTopMissingSkills(startDate, deptFilter);
            for (Object[] r : rows) {
                if (r[0] != null && r[1] != null) {
                    list.add(new MissingSkillCount(r[0].toString(), ((Number) r[1]).longValue()));
                }
            }
        } catch (Exception e) {
            log.warn("Error retrieving top missing skills: {}", e.getMessage());
        }
        return list;
    }

    private AiManagerAgreementStats buildAiManagerAgreement(LocalDate startDate, String deptFilter) {
        List<Candidate> candidates = candidateRepository.findAll().stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> c.getAiRecommendation() != null && !c.getAiRecommendation().isBlank())
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .collect(Collectors.toList());

        List<Interview> interviews = interviewRepository.findAll();

        long compFav = 0, compRes = 0, compDef = 0;
        long aExFav = 0, aExRes = 0, aExDef = 0;
        long nonCompFav = 0, nonCompRes = 0, nonCompDef = 0;

        for (Candidate c : candidates) {
            Interview evalIt = interviews.stream()
                    .filter(i -> i.getCandidate() != null && Objects.equals(i.getCandidate().getId(), c.getId()))
                    .filter(i -> i.getManagerRecommendation() != null)
                    .max(Comparator.comparing(Interview::getInterviewDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);

            if (evalIt == null) continue;

            String aiRec = c.getAiRecommendation().toUpperCase().trim();
            ManagerAvis mgrRec = evalIt.getManagerRecommendation();

            if ("COMPATIBLE".equals(aiRec)) {
                if (mgrRec == ManagerAvis.FAVORABLE) compFav++;
                else if (mgrRec == ManagerAvis.RESERVE) compRes++;
                else if (mgrRec == ManagerAvis.DEFAVORABLE) compDef++;
            } else if ("A_EXAMINER".equals(aiRec)) {
                if (mgrRec == ManagerAvis.FAVORABLE) aExFav++;
                else if (mgrRec == ManagerAvis.RESERVE) aExRes++;
                else if (mgrRec == ManagerAvis.DEFAVORABLE) aExDef++;
            } else if ("NON_COMPATIBLE".equals(aiRec)) {
                if (mgrRec == ManagerAvis.FAVORABLE) nonCompFav++;
                else if (mgrRec == ManagerAvis.RESERVE) nonCompRes++;
                else if (mgrRec == ManagerAvis.DEFAVORABLE) nonCompDef++;
            }
        }

        long totalPairs = compFav + compRes + compDef + aExFav + aExRes + aExDef + nonCompFav + nonCompRes + nonCompDef;
        long concordantPairs = compFav + aExRes + nonCompDef;
        Double agreementRate = totalPairs > 0 ? Math.round(((double) concordantPairs / totalPairs) * 1000.0) / 1000.0 : null;

        AgreementMatrix matrix = new AgreementMatrix(
                compFav, compRes, compDef,
                aExFav, aExRes, aExDef,
                nonCompFav, nonCompRes, nonCompDef
        );

        return new AiManagerAgreementStats(matrix, agreementRate, totalPairs);
    }

    private ActionsRequiredStats buildActionsRequired(LocalDate today, String deptFilter) {
        LocalDate cutoff7Days = today.minusDays(7);

        List<Candidate> allCandidates = candidateRepository.findAll();
        List<Candidate> unprocessedCandidates = allCandidates.stream()
                .filter(c -> c.getStatus() == CandidateStatus.NOUVELLE)
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isAfter(cutoff7Days))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .sorted(Comparator.comparing(Candidate::getApplicationDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        long totalUnprocessed = unprocessedCandidates.size();
        List<UnprocessedApplicationDto> top5Unprocessed = unprocessedCandidates.stream()
                .limit(5)
                .map(c -> new UnprocessedApplicationDto(
                        c.getId(),
                        c.getFirstName() + " " + c.getLastName(),
                        c.getJobOffer() != null ? c.getJobOffer().getTitle() : "Sans poste",
                        ChronoUnit.DAYS.between(c.getApplicationDate(), today)
                ))
                .collect(Collectors.toList());

        List<Candidate> aiErrorCandidates = allCandidates.stream()
                .filter(c -> c.getAiStatus() == StatutAnalyse.ERREUR)
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .sorted(Comparator.comparing(Candidate::getId).reversed())
                .collect(Collectors.toList());

        long totalAiErrors = aiErrorCandidates.size();
        List<AiErrorDto> top5AiErrors = aiErrorCandidates.stream()
                .limit(5)
                .map(c -> new AiErrorDto(
                        c.getId(),
                        c.getFirstName() + " " + c.getLastName(),
                        c.getJobOffer() != null ? c.getJobOffer().getTitle() : "Sans poste",
                        c.getAiError() != null ? c.getAiError() : "Erreur inconnue"
                ))
                .collect(Collectors.toList());

        List<Interview> allInterviews = interviewRepository.findAll();
        List<Interview> missingFeedbacksList = allInterviews.stream()
                .filter(i -> i.getInterviewDate() != null && i.getInterviewDate().isBefore(today))
                .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                .filter(i -> i.getManagerRecommendation() == null)
                .filter(i -> deptFilter == null || (i.getJobOffer() != null && deptFilter.equalsIgnoreCase(i.getJobOffer().getDepartment())))
                .sorted(Comparator.comparing(Interview::getInterviewDate).reversed())
                .collect(Collectors.toList());

        long totalMissingFeedbacks = missingFeedbacksList.size();
        List<MissingFeedbackDto> top5MissingFeedbacks = missingFeedbacksList.stream()
                .limit(5)
                .map(i -> new MissingFeedbackDto(
                        i.getId(),
                        i.getCandidate() != null ? i.getCandidate().getFirstName() + " " + i.getCandidate().getLastName() : "Non spécifié",
                        i.getManager() != null ? i.getManager().getFirstName() + " " + i.getManager().getLastName() : "Non assigné",
                        i.getInterviewDate()
                ))
                .collect(Collectors.toList());

        LocalDate endOfWeek = today.plusDays(7);
        List<JobOffer> allOffers = jobOfferRepository.findAll();
        List<JobOffer> expiringList = allOffers.stream()
                .filter(j -> j.getStatus() == JobOfferStatus.PUBLISHED)
                .filter(j -> j.getApplicationDeadline() != null && !j.getApplicationDeadline().isBefore(today) && !j.getApplicationDeadline().isAfter(endOfWeek))
                .filter(j -> deptFilter == null || deptFilter.equalsIgnoreCase(j.getDepartment()))
                .sorted(Comparator.comparing(JobOffer::getApplicationDeadline))
                .collect(Collectors.toList());

        long totalExpiringOffers = expiringList.size();
        List<ExpiringOfferDto> top5ExpiringOffers = expiringList.stream()
                .limit(5)
                .map(j -> new ExpiringOfferDto(
                        j.getId(),
                        j.getTitle(),
                        j.getApplicationDeadline()
                ))
                .collect(Collectors.toList());

        return new ActionsRequiredStats(
                top5Unprocessed, totalUnprocessed,
                top5AiErrors, totalAiErrors,
                top5MissingFeedbacks, totalMissingFeedbacks,
                top5ExpiringOffers, totalExpiringOffers
        );
    }

    private List<UpcomingInterviewDto> buildUpcomingInterviews(LocalDate today, String deptFilter) {
        LocalDate endOfWeek = today.plusDays(7);
        return interviewRepository.findAll().stream()
                .filter(i -> i.getStatus() == InterviewStatus.PLANIFIE)
                .filter(i -> i.getInterviewDate() != null && !i.getInterviewDate().isBefore(today) && !i.getInterviewDate().isAfter(endOfWeek))
                .filter(i -> deptFilter == null || (i.getJobOffer() != null && deptFilter.equalsIgnoreCase(i.getJobOffer().getDepartment())))
                .sorted(Comparator.comparing(Interview::getInterviewDate)
                        .thenComparing(Interview::getInterviewTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new UpcomingInterviewDto(
                        i.getId(),
                        i.getInterviewDate(),
                        i.getInterviewTime(),
                        i.getCandidate() != null ? i.getCandidate().getFirstName() + " " + i.getCandidate().getLastName() : "Candidat",
                        i.getJobOffer() != null ? i.getJobOffer().getTitle() : "Poste",
                        i.getManager() != null ? i.getManager().getFirstName() + " " + i.getManager().getLastName() : "Manager",
                        i.getAiQuestionsStatus() != null ? i.getAiQuestionsStatus().name() : null
                ))
                .collect(Collectors.toList());
    }

    private List<TopOfferDto> buildTopOffers(LocalDate startDate, String deptFilter) {
        List<Candidate> candidates = candidateRepository.findAll().stream()
                .filter(c -> c.getApplicationDate() != null && !c.getApplicationDate().isBefore(startDate))
                .filter(c -> deptFilter == null || (c.getJobOffer() != null && deptFilter.equalsIgnoreCase(c.getJobOffer().getDepartment())))
                .collect(Collectors.toList());

        Map<JobOffer, List<Candidate>> candidatesByOffer = candidates.stream()
                .filter(c -> c.getJobOffer() != null)
                .collect(Collectors.groupingBy(Candidate::getJobOffer));

        return candidatesByOffer.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(5)
                .map(entry -> {
                    JobOffer offer = entry.getKey();
                    List<Candidate> offerCandidates = entry.getValue();

                    long appCount = offerCandidates.size();

                    OptionalDouble avgScoreOpt = offerCandidates.stream()
                            .filter(c -> c.getAiScore() != null)
                            .mapToDouble(Candidate::getAiScore)
                            .average();
                    Double avgScore = avgScoreOpt.isPresent() ? Math.round(avgScoreOpt.getAsDouble() * 100.0) / 100.0 : null;

                    Candidate bestCandidate = offerCandidates.stream()
                            .filter(c -> c.getAiScore() != null)
                            .max(Comparator.comparing(Candidate::getAiScore))
                            .orElse(null);

                    String bestName = bestCandidate != null ? bestCandidate.getFirstName() + " " + bestCandidate.getLastName() : null;
                    Double bestScore = bestCandidate != null ? bestCandidate.getAiScore() : null;

                    return new TopOfferDto(
                            offer.getId(),
                            offer.getTitle(),
                            offer.getDepartment(),
                            appCount,
                            avgScore,
                            bestName,
                            bestScore
                    );
                })
                .collect(Collectors.toList());
    }

    private List<DepartmentLeavesDto> buildLeavesByDepartment() {
        List<Department> departments = departmentRepository.findAll();
        List<Leave> leaves = leaveRepository.findAll();
        List<Employee> employees = employeeRepository.findAll();

        Map<Long, String> empDeptMap = employees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.toMap(Employee::getId, e -> e.getDepartment().getName(), (d1, d2) -> d1));

        Map<String, Long> deptLeavesCount = new HashMap<>();

        for (Leave l : leaves) {
            String deptName = empDeptMap.get(l.getEmployeeId());
            if (deptName != null) {
                deptLeavesCount.put(deptName, deptLeavesCount.getOrDefault(deptName, 0L) + 1);
            }
        }

        List<DepartmentLeavesDto> list = new ArrayList<>();
        for (Department d : departments) {
            long count = deptLeavesCount.getOrDefault(d.getName(), 0L);
            list.add(new DepartmentLeavesDto(d.getName(), count));
        }

        return list;
    }

    private AiServiceStats buildAiServiceStats() {
        boolean online = false;
        String methode = "IA Offline";
        LocalDateTime checkedAt = LocalDateTime.now();

        try {
            Map<?, ?> healthResp = aiRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);

            if (healthResp != null && "ok".equalsIgnoreCase(String.valueOf(healthResp.get("status")))) {
                online = true;
                methode = healthResp.get("methode") != null ? String.valueOf(healthResp.get("methode")) : "regles-v1";
            }
        } catch (Exception e) {
            log.info("AI Service health check failed: {}", e.getMessage());
            online = false;
            methode = "IA Offline";
        }

        LocalDateTime lastAnalysisAt = null;
        long analysesLast30Days = 0;
        try {
            List<Candidate> all = candidateRepository.findAll();
            lastAnalysisAt = all.stream()
                    .map(Candidate::getAiAnalysisDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            LocalDateTime since30Days = LocalDateTime.now().minusDays(30);
            analysesLast30Days = all.stream()
                    .filter(c -> c.getAiAnalysisDate() != null && !c.getAiAnalysisDate().isBefore(since30Days))
                    .count();
        } catch (Exception e) {
            log.warn("Error fetching AI analysis stats: {}", e.getMessage());
        }

        return new AiServiceStats(online, methode, checkedAt, lastAnalysisAt, analysesLast30Days);
    }
}
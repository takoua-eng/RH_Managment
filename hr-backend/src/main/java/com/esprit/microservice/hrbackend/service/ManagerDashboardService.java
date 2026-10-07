package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.*;
import com.esprit.microservice.hrbackend.dto.ManagerDashboardStatsDTO.*;
import com.esprit.microservice.hrbackend.dto.ManagerDashboardStatsDTO.TeamAbsencesStats.*;
import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ManagerDashboardService.class);

    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public ManagerDashboardDTO getManagerDashboard(String keycloakId, Long managerIdOverride) {
        Employee manager = null;
        if (managerIdOverride != null) {
            manager = employeeRepository.findById(managerIdOverride).orElse(null);
        }
        if (manager == null && keycloakId != null) {
            manager = employeeRepository.findByKeycloakId(keycloakId).orElse(null);
        }

        if (manager == null) {
            return ManagerDashboardDTO.builder()
                    .totalEmployees(0)
                    .activeEmployees(0)
                    .pendingLeaves(0)
                    .enrolledEmployeesCount(0)
                    .activeTrainings(0)
                    .completedTrainings(0)
                    .averageProgression(0.0)
                    .topTrainings(Collections.emptyMap())
                    .teamMembers(Collections.emptyList())
                    .recentNotifications(Collections.emptyList())
                    .build();
        }

        Long managerId = manager.getId();
        List<Employee> team = employeeRepository.findByManagerId(managerId).stream()
                .filter(e -> !e.getId().equals(managerId))
                .sorted(Comparator.comparing(Employee::getFirstName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        int totalEmployees = team.size();
        int activeEmployees = (int) team.stream()
                .filter(e -> e.getStatus() == null || e.getStatus() == Status.ACTIVE)
                .count();

        List<EmployeeSummaryDTO> teamMembers = team.stream()
                .map(e -> {
                    String photoUrl = e.getPhotoPath() != null ? "http://localhost:8087/api/employees/" + e.getId() + "/photo" : null;
                    return new EmployeeSummaryDTO(e.getId(), e.getFirstName(), e.getLastName(), e.getPosition(), photoUrl);
                })
                .collect(Collectors.toList());

        Set<Long> teamEmpIds = team.stream().map(Employee::getId).collect(Collectors.toSet());

        int pendingLeaves = 0;
        if (!teamEmpIds.isEmpty()) {
            List<Leave> leaves = leaveRepository.findByEmployeeIdInAndStatusIn(
                    teamEmpIds,
                    List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
            );
            pendingLeaves = leaves.size();
        }

        int enrolledEmployeesCount = 0;
        int activeTrainings = 0;
        int completedTrainings = 0;
        double averageProgression = 0.0;
        Map<String, Long> topTrainings = new LinkedHashMap<>();

        if (!teamEmpIds.isEmpty()) {
            List<TrainingEnrollment> enrollments = trainingEnrollmentRepository.findByEmployeeIdIn(teamEmpIds);
            enrolledEmployeesCount = (int) enrollments.stream().map(TrainingEnrollment::getEmployee).map(Employee::getId).distinct().count();
            activeTrainings = (int) enrollments.stream().filter(e -> "En cours".equalsIgnoreCase(e.getStatus()) || "À venir".equalsIgnoreCase(e.getStatus()) || "INSCRIT".equalsIgnoreCase(e.getStatus())).count();
            completedTrainings = (int) enrollments.stream().filter(e -> "Terminée".equalsIgnoreCase(e.getStatus()) || "TERMINE".equalsIgnoreCase(e.getStatus())).count();
            averageProgression = enrollments.stream().mapToDouble(e -> e.getProgression() != null ? e.getProgression() : 0.0).average().orElse(0.0);
            averageProgression = Math.round(averageProgression * 10.0) / 10.0;

            Map<String, Long> counts = enrollments.stream()
                    .filter(e -> e.getTraining() != null && e.getTraining().getTitle() != null)
                    .collect(Collectors.groupingBy(e -> e.getTraining().getTitle(), Collectors.counting()));

            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> topTrainings.put(entry.getKey(), entry.getValue()));
        }

        List<NotificationDTO> notifs = notificationService.getNotifications(manager.getId());
        List<ManagerNotificationDTO> recentNotifications = notifs.stream()
                .map(n -> new ManagerNotificationDTO(n.getId(), n.getText(), n.getTime(), n.getIcon(), n.isUnread(), n.getType(), n.getLink()))
                .collect(Collectors.toList());

        return ManagerDashboardDTO.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .pendingLeaves(pendingLeaves)
                .enrolledEmployeesCount(enrolledEmployeesCount)
                .activeTrainings(activeTrainings)
                .completedTrainings(completedTrainings)
                .averageProgression(averageProgression)
                .topTrainings(topTrainings)
                .teamMembers(teamMembers)
                .recentNotifications(recentNotifications)
                .build();
    }

    @Transactional(readOnly = true)
    public ManagerDashboardStatsDTO getManagerDashboardStats(String keycloakId) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        Employee manager = employeeRepository.findByKeycloakId(keycloakId).orElse(null);
        List<Candidate> candidateScope = getCandidateScope(keycloakId, manager);
        List<Interview> managerInterviews = getManagerInterviews(keycloakId, manager);

        Kpis kpis = computeKpis(candidateScope, managerInterviews, today);
        InterviewDetailsDto nextInterview = computeNextInterview(managerInterviews, today, nowTime);
        List<InterviewDetailsDto> upcomingInterviews = computeUpcomingInterviews(managerInterviews, today);
        List<CandidateToProcessDto> candidatesToProcess = computeCandidatesToProcess(candidateScope, managerInterviews);
        List<PendingFeedbackDto> pendingFeedbacks = computePendingFeedbacks(managerInterviews, today);
        MyEvaluationsStats myEvaluations = computeMyEvaluations(managerInterviews);
        TeamAbsencesStats teamAbsences = computeTeamAbsences(manager, today);

        return new ManagerDashboardStatsDTO(
            kpis,
            nextInterview,
            upcomingInterviews,
            candidatesToProcess,
            pendingFeedbacks,
            myEvaluations,
            teamAbsences
        );
    }

    private List<Candidate> getCandidateScope(String keycloakId, Employee manager) {
        List<Candidate> candidates = new ArrayList<>();
        try {
            if (keycloakId != null) {
                candidates.addAll(candidateRepository.findByAssignedManagerKeycloakId(keycloakId));
            }
            if (manager != null && manager.getId() != null) {
                Set<Long> existingIds = candidates.stream().map(Candidate::getId).collect(Collectors.toSet());
                List<Candidate> byManagerId = candidateRepository.findByAssignedManagerId(manager.getId());
                for (Candidate c : byManagerId) {
                    if (!existingIds.contains(c.getId())) {
                        candidates.add(c);
                        existingIds.add(c.getId());
                    }
                }
            }
            if (manager != null && manager.getDepartment() != null && manager.getDepartment().getName() != null) {
                String deptName = manager.getDepartment().getName();
                Set<Long> existingIds = candidates.stream().map(Candidate::getId).collect(Collectors.toSet());
                List<Candidate> all = candidateRepository.findAll();
                for (Candidate c : all) {
                    if (!existingIds.contains(c.getId()) && c.getJobOffer() != null && deptName.equalsIgnoreCase(c.getJobOffer().getDepartment())) {
                        candidates.add(c);
                        existingIds.add(c.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération du périmètre des candidats: {}", e.getMessage(), e);
        }
        return candidates;
    }

    private List<Interview> getManagerInterviews(String keycloakId, Employee manager) {
        try {
            Set<Interview> interviews = new HashSet<>();
            if (keycloakId != null) {
                interviews.addAll(interviewRepository.findByManagerKeycloakId(keycloakId));
            }
            if (manager != null && manager.getId() != null) {
                interviews.addAll(interviewRepository.findByManagerId(manager.getId()));
            }
            return new ArrayList<>(interviews);
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des entretiens du manager: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private LocalTime parseInterviewTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        String trimmed = timeStr.trim();
        try {
            if (trimmed.length() == 4 && trimmed.charAt(1) == ':') {
                trimmed = "0" + trimmed;
            }
            return LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(trimmed);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private Kpis computeKpis(List<Candidate> candidateScope, List<Interview> managerInterviews, LocalDate today) {
        try {
            List<Interview> allInterviews = interviewRepository.findAll();
            Set<Long> candidateIdsWithActiveInterview = allInterviews.stream()
                    .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                    .map(i -> i.getCandidate() != null ? i.getCandidate().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            long candidatesToProcess = candidateScope.stream()
                    .filter(c -> c.getStatus() == CandidateStatus.TRANSMISE_MANAGER)
                    .filter(c -> !candidateIdsWithActiveInterview.contains(c.getId()))
                    .count();

            LocalDate endOfWeek = today.plusDays(7);
            long interviewsThisWeek = managerInterviews.stream()
                    .filter(i -> i.getStatus() == InterviewStatus.PLANIFIE)
                    .filter(i -> i.getInterviewDate() != null && !i.getInterviewDate().isBefore(today) && !i.getInterviewDate().isAfter(endOfWeek))
                    .count();

            long pendingFeedbacks = managerInterviews.stream()
                    .filter(i -> i.getInterviewDate() != null && i.getInterviewDate().isBefore(today))
                    .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                    .filter(i -> i.getManagerRecommendation() == null)
                    .count();

            OptionalDouble avgScoreOpt = candidateScope.stream()
                    .filter(c -> c.getAiScore() != null)
                    .mapToDouble(Candidate::getAiScore)
                    .average();

            Double avgAiScore = avgScoreOpt.isPresent() ? Math.round(avgScoreOpt.getAsDouble() * 100.0) / 100.0 : null;

            return new Kpis(candidatesToProcess, interviewsThisWeek, pendingFeedbacks, avgAiScore);
        } catch (Exception e) {
            log.warn("Erreur dans la section kpis: {}", e.getMessage(), e);
            return new Kpis(0, 0, 0, null);
        }
    }

    private InterviewDetailsDto computeNextInterview(List<Interview> managerInterviews, LocalDate today, LocalTime nowTime) {
        try {
            return managerInterviews.stream()
                    .filter(i -> i.getStatus() == InterviewStatus.PLANIFIE)
                    .filter(i -> i.getInterviewDate() != null)
                    .filter(i -> {
                        if (i.getInterviewDate().isAfter(today)) return true;
                        if (i.getInterviewDate().isEqual(today)) {
                            LocalTime itTime = parseInterviewTime(i.getInterviewTime());
                            if (itTime == null) return true;
                            return !itTime.isBefore(nowTime);
                        }
                        return false;
                    })
                    .sorted(Comparator.comparing(Interview::getInterviewDate)
                            .thenComparing(i -> parseInterviewTime(i.getInterviewTime()), Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(this::mapToInterviewDetailsDto)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Erreur dans la section nextInterview: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<InterviewDetailsDto> computeUpcomingInterviews(List<Interview> managerInterviews, LocalDate today) {
        try {
            LocalDate endOfWeek = today.plusDays(7);
            return managerInterviews.stream()
                    .filter(i -> i.getStatus() == InterviewStatus.PLANIFIE)
                    .filter(i -> i.getInterviewDate() != null && !i.getInterviewDate().isBefore(today) && !i.getInterviewDate().isAfter(endOfWeek))
                    .sorted(Comparator.comparing(Interview::getInterviewDate)
                            .thenComparing(i -> parseInterviewTime(i.getInterviewTime()), Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(this::mapToInterviewDetailsDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Erreur dans la section upcomingInterviews: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<CandidateToProcessDto> computeCandidatesToProcess(List<Candidate> candidateScope, List<Interview> managerInterviews) {
        try {
            List<Interview> allInterviews = interviewRepository.findAll();
            Set<Long> candidateIdsWithActiveInterview = allInterviews.stream()
                    .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                    .map(i -> i.getCandidate() != null ? i.getCandidate().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            return candidateScope.stream()
                    .filter(c -> c.getStatus() == CandidateStatus.TRANSMISE_MANAGER)
                    .filter(c -> !candidateIdsWithActiveInterview.contains(c.getId()))
                    .sorted(Comparator.comparing(Candidate::getAiScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(10)
                    .map(c -> new CandidateToProcessDto(
                            c.getId(),
                            c.getFirstName() + " " + c.getLastName(),
                            c.getJobOffer() != null ? c.getJobOffer().getTitle() : "Sans poste",
                            c.getApplicationDate(),
                            c.getAiScore(),
                            c.getAiRecommendation(),
                            c.getAiStatus()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Erreur dans la section candidatesToProcess: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<PendingFeedbackDto> computePendingFeedbacks(List<Interview> managerInterviews, LocalDate today) {
        try {
            return managerInterviews.stream()
                    .filter(i -> i.getInterviewDate() != null && i.getInterviewDate().isBefore(today))
                    .filter(i -> i.getStatus() != InterviewStatus.ANNULE)
                    .filter(i -> i.getManagerRecommendation() == null)
                    .sorted(Comparator.comparing(Interview::getInterviewDate))
                    .limit(10)
                    .map(i -> new PendingFeedbackDto(
                            i.getId(),
                            i.getCandidate() != null ? i.getCandidate().getFirstName() + " " + i.getCandidate().getLastName() : "Candidat",
                            i.getJobOffer() != null ? i.getJobOffer().getTitle() : "Poste",
                            i.getInterviewDate(),
                            ChronoUnit.DAYS.between(i.getInterviewDate(), today)
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Erreur dans la section pendingFeedbacks: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private MyEvaluationsStats computeMyEvaluations(List<Interview> managerInterviews) {
        try {
            List<Interview> evaluated = managerInterviews.stream()
                    .filter(i -> i.getManagerRecommendation() != null)
                    .collect(Collectors.toList());

            long favorable = evaluated.stream().filter(i -> i.getManagerRecommendation() == ManagerAvis.FAVORABLE).count();
            long reserve = evaluated.stream().filter(i -> i.getManagerRecommendation() == ManagerAvis.RESERVE).count();
            long defavorable = evaluated.stream().filter(i -> i.getManagerRecommendation() == ManagerAvis.DEFAVORABLE).count();
            long totalEvaluated = favorable + reserve + defavorable;

            OptionalDouble avgRatingOpt = evaluated.stream()
                    .filter(i -> i.getManagerRating() != null)
                    .mapToDouble(Interview::getManagerRating)
                    .average();

            Double averageRating = avgRatingOpt.isPresent() ? Math.round(avgRatingOpt.getAsDouble() * 10.0) / 10.0 : null;

            long concordantCount = 0;
            long totalPairs = 0;

            for (Interview i : evaluated) {
                Candidate c = i.getCandidate();
                if (c != null && c.getAiRecommendation() != null && !c.getAiRecommendation().isBlank()) {
                    totalPairs++;
                    String aiRec = c.getAiRecommendation().toUpperCase().trim();
                    ManagerAvis mgrRec = i.getManagerRecommendation();

                    if (("COMPATIBLE".equals(aiRec) && mgrRec == ManagerAvis.FAVORABLE) ||
                        ("A_EXAMINER".equals(aiRec) && mgrRec == ManagerAvis.RESERVE) ||
                        ("NON_COMPATIBLE".equals(aiRec) && mgrRec == ManagerAvis.DEFAVORABLE)) {
                        concordantCount++;
                    }
                }
            }

            Double agreementWithAi = totalPairs > 0 ? Math.round(((double) concordantCount / totalPairs) * 1000.0) / 1000.0 : null;

            return new MyEvaluationsStats(favorable, reserve, defavorable, totalEvaluated, averageRating, agreementWithAi);
        } catch (Exception e) {
            log.warn("Erreur dans la section myEvaluations: {}", e.getMessage(), e);
            return new MyEvaluationsStats(0, 0, 0, 0, null, null);
        }
    }

    private TeamAbsencesStats computeTeamAbsences(Employee manager, LocalDate today) {
        try {
            if (manager == null) {
                return new TeamAbsencesStats(Collections.emptyList(), Collections.emptyList());
            }

            List<Employee> teamEmployees = employeeRepository.findByManagerId(manager.getId()).stream()
                    .filter(e -> !e.getId().equals(manager.getId()))
                    .collect(Collectors.toList());

            if (teamEmployees.isEmpty()) {
                return new TeamAbsencesStats(Collections.emptyList(), Collections.emptyList());
            }

            Map<Long, Employee> teamMap = teamEmployees.stream().collect(Collectors.toMap(Employee::getId, e -> e, (e1, e2) -> e1));
            Set<Long> teamEmpIds = teamMap.keySet();

            List<Leave> teamLeaves = leaveRepository.findByEmployeeIdInAndStatusIn(
                    teamEmpIds,
                    List.of(LeaveStatus.APPROVED, LeaveStatus.APPROVED)
            );

            List<AbsentTodayDto> absentToday = teamLeaves.stream()
                    .filter(l -> l.getStartDate() != null && l.getEndDate() != null)
                    .filter(l -> !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                    .map(l -> {
                        Employee emp = teamMap.get(l.getEmployeeId());
                        String name = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employé";
                        return new AbsentTodayDto(name, l.getEndDate().plusDays(1));
                    })
                    .collect(Collectors.toList());

            LocalDate cutoff14Days = today.plusDays(14);
            List<UpcomingLeaveDto> upcomingLeaves = teamLeaves.stream()
                    .filter(l -> l.getStartDate() != null && l.getEndDate() != null)
                    .filter(l -> l.getStartDate().isAfter(today) && !l.getStartDate().isAfter(cutoff14Days))
                    .sorted(Comparator.comparing(Leave::getStartDate))
                    .map(l -> {
                        Employee emp = teamMap.get(l.getEmployeeId());
                        String name = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employé";
                        return new UpcomingLeaveDto(name, l.getStartDate(), l.getEndDate());
                    })
                    .collect(Collectors.toList());

            return new TeamAbsencesStats(absentToday, upcomingLeaves);
        } catch (Exception e) {
            log.warn("Erreur dans la section teamAbsences: {}", e.getMessage(), e);
            return new TeamAbsencesStats(Collections.emptyList(), Collections.emptyList());
        }
    }

    private InterviewDetailsDto mapToInterviewDetailsDto(Interview i) {
        Candidate c = i.getCandidate();
        JobOffer j = i.getJobOffer();
        return new InterviewDetailsDto(
                i.getId(),
                c != null ? c.getId() : null,
                c != null ? c.getFirstName() + " " + c.getLastName() : "Candidat",
                j != null ? j.getTitle() : "Poste",
                i.getInterviewDate(),
                i.getInterviewTime(),
                i.getDurationMinutes(),
                i.getType(),
                i.getLocationOrLink(),
                i.getAiQuestionsStatus(),
                c != null ? c.getAiScore() : null,
                c != null ? c.getAiRecommendation() : null
        );
    }
}

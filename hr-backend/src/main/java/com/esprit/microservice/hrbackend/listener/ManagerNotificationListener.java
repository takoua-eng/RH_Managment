package com.esprit.microservice.hrbackend.listener;

import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.event.*;
import com.esprit.microservice.hrbackend.repository.*;
import com.esprit.microservice.hrbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManagerNotificationListener {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final LeaveRepository leaveRepository;
    private final TrainingRepository trainingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleEmployeeAddedToTeam(EmployeeAddedToTeamEvent event) {
        try {
            if (event.employeeId() == null) return;
            Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
            if (employee == null) return;

            String employeeName = employee.getFirstName() + " " + employee.getLastName();
            String position = (employee.getPosition() != null && !employee.getPosition().isBlank())
                    ? employee.getPosition().trim()
                    : "collaborateur";

            // 1. Notify Manager
            if (event.managerId() != null && !event.managerId().equals(event.employeeId())) {
                String textManager = employeeName + " a rejoint votre équipe (" + position + ")";
                notificationService.notify(
                        event.managerId(),
                        NotificationType.EQUIPE,
                        textManager,
                        "/manager/team/" + employee.getId()
                );
            }

            // 2. Notify Employee
            String textEmployee = "Vous avez été affecté à une équipe";
            if (event.managerId() != null) {
                Employee mgr = employeeRepository.findById(event.managerId()).orElse(null);
                if (mgr != null) {
                    textEmployee = "Vous avez été affecté à l'équipe de " + mgr.getFirstName() + " " + mgr.getLastName();
                }
            }
            notificationService.notify(
                    employee.getId(),
                    NotificationType.EQUIPE,
                    textEmployee,
                    "/employee/profile"
            );
        } catch (Exception e) {
            log.error("Error in handleEmployeeAddedToTeam [employeeId={}]: {}", event.employeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleEmployeeRemovedFromTeam(EmployeeRemovedFromTeamEvent event) {
        try {
            if (event.employeeId() == null) return;
            Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
            if (employee == null) return;

            String employeeName = employee.getFirstName() + " " + employee.getLastName();

            // Notify old manager
            if (event.oldManagerId() != null && !event.oldManagerId().equals(event.newManagerId())) {
                String text = employeeName + " ne fait plus partie de votre équipe";
                notificationService.notify(
                        event.oldManagerId(),
                        NotificationType.EQUIPE,
                        text,
                        "/manager/team"
                );
            }

            // Notify employee
            notificationService.notify(
                    employee.getId(),
                    NotificationType.EQUIPE,
                    "Votre responsable d'équipe a été mis à jour",
                    "/employee/profile"
            );
        } catch (Exception e) {
            log.error("Error in handleEmployeeRemovedFromTeam [employeeId={}]: {}", event.employeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleEmployeeDepartmentChanged(EmployeeDepartmentChangedEvent event) {
        try {
            if (event.employeeId() == null) return;
            notificationService.notify(
                    event.employeeId(),
                    NotificationType.EQUIPE,
                    "Votre département a été mis à jour : " + event.departmentName(),
                    "/employee/profile"
            );
        } catch (Exception e) {
            log.error("Error in handleEmployeeDepartmentChanged [employeeId={}]: {}", event.employeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCandidatureTransmise(CandidatureTransmiseEvent event) {
        try {
            if (event.managerId() == null || event.candidateId() == null) return;

            Candidate candidate = candidateRepository.findById(event.candidateId()).orElse(null);
            if (candidate == null) return;

            String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
            String jobTitle = candidate.getJobOffer() != null ? candidate.getJobOffer().getTitle() : "Poste";

            String scoreText = "";
            if (candidate.getAiScore() != null) {
                double rawScore = candidate.getAiScore();
                long pct = rawScore <= 1.0 ? Math.round(rawScore * 100.0) : Math.round(rawScore);
                scoreText = " (score IA : " + pct + " %)";
            }

            String text = "Nouvelle candidature à évaluer : " + candidateName + " – " + jobTitle + scoreText;

            notificationService.notify(
                    event.managerId(),
                    NotificationType.CANDIDATURE,
                    text,
                    "/manager/recruitment/" + candidate.getId()
            );
        } catch (Exception e) {
            log.error("Error in handleCandidatureTransmise [candidateId={}, managerId={}]: {}",
                    event.candidateId(), event.managerId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCandidateAiAnalysisCompleted(CandidateAiAnalysisCompletedEvent event) {
        try {
            if (event.candidateId() == null) return;

            Candidate candidate = candidateRepository.findById(event.candidateId()).orElse(null);
            if (candidate == null) return;

            String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
            String scoreText = "";
            if (candidate.getAiScore() != null) {
                double rawScore = candidate.getAiScore();
                long pct = rawScore <= 1.0 ? Math.round(rawScore * 100.0) : Math.round(rawScore);
                scoreText = " (Score : " + pct + " %)";
            }

            String text = "Analyse IA terminée pour " + candidateName + scoreText;

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.IA,
                    text,
                    "/recruitment/candidates/" + candidate.getId(),
                    null
            );
        } catch (Exception e) {
            log.error("Error in handleCandidateAiAnalysisCompleted [candidateId={}]: {}", event.candidateId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCandidateAiAnalysisError(CandidateAiAnalysisErrorEvent event) {
        try {
            if (event.candidateId() == null) return;
            Candidate candidate = candidateRepository.findById(event.candidateId()).orElse(null);
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat #" + event.candidateId();

            String text = "Erreur lors de l'analyse IA de " + candidateName + " : " + (event.errorMessage() != null ? event.errorMessage() : "erreur inconnue");

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN),
                    NotificationType.IA,
                    text,
                    "/recruitment/candidates/" + event.candidateId(),
                    null
            );
        } catch (Exception e) {
            log.error("Error in handleCandidateAiAnalysisError [candidateId={}]: {}", event.candidateId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleEntretienAttribue(EntretienAttribueEvent event) {
        try {
            if (event.interviewId() == null) return;

            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null || interview.getManager() == null) return;

            Employee manager = interview.getManager();

            if (event.scheduledByUsername() != null) {
                String scheduler = event.scheduledByUsername();
                if ((manager.getKeycloakId() != null && manager.getKeycloakId().equals(scheduler)) ||
                    (manager.getEmail() != null && manager.getEmail().equalsIgnoreCase(scheduler))) {
                    return;
                }
            }

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";

            String formattedDate = interview.getInterviewDate() != null
                    ? interview.getInterviewDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "";
            String timeStr = interview.getInterviewTime() != null ? interview.getInterviewTime() : "";

            String text = "Entretien planifié le " + formattedDate + " à " + timeStr + " avec " + candidateName;

            notificationService.notify(
                    manager.getId(),
                    NotificationType.ENTRETIEN,
                    text,
                    "/manager/interviews/" + interview.getId()
            );
        } catch (Exception e) {
            log.error("Error in handleEntretienAttribue [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInterviewScheduledByManager(InterviewScheduledByManagerEvent event) {
        try {
            if (event.interviewId() == null) return;
            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null) return;

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";

            String text = "Un entretien a été planifié avec " + candidateName;

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.ENTRETIEN,
                    text,
                    "/recruitment/interviews/" + interview.getId(),
                    event.managerId()
            );
        } catch (Exception e) {
            log.error("Error in handleInterviewScheduledByManager [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInterviewUpdated(InterviewUpdatedEvent event) {
        try {
            if (event.interviewId() == null) return;
            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null) return;

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";

            String text = "L'entretien avec " + candidateName + " a été mis à jour";

            // Notify manager if manager exists and is not actor
            if (interview.getManager() != null && !interview.getManager().getId().equals(event.actorEmployeeId())) {
                notificationService.notify(
                        interview.getManager().getId(),
                        NotificationType.ENTRETIEN,
                        text,
                        "/manager/interviews/" + interview.getId()
                );
            }

            // Notify Admin & RH
            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.ENTRETIEN,
                    text,
                    "/recruitment/interviews/" + interview.getId(),
                    event.actorEmployeeId()
            );
        } catch (Exception e) {
            log.error("Error in handleInterviewUpdated [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInterviewCancelled(InterviewCancelledEvent event) {
        try {
            if (event.interviewId() == null) return;
            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null) return;

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";

            String reason = (event.reason() != null && !event.reason().isBlank()) ? " (" + event.reason() + ")" : "";
            String text = "L'entretien avec " + candidateName + " a été annulé" + reason;

            if (interview.getManager() != null && !interview.getManager().getId().equals(event.actorEmployeeId())) {
                notificationService.notify(
                        interview.getManager().getId(),
                        NotificationType.ENTRETIEN,
                        text,
                        "/manager/interviews/" + interview.getId()
                );
            }

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.ENTRETIEN,
                    text,
                    "/recruitment/interviews/" + interview.getId(),
                    event.actorEmployeeId()
            );
        } catch (Exception e) {
            log.error("Error in handleInterviewCancelled [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInterviewAiQuestionsReady(InterviewAiQuestionsReadyEvent event) {
        try {
            if (event.interviewId() == null) return;
            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null || interview.getManager() == null) return;

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";

            String text = "Le guide d'entretien IA est disponible pour l'entretien avec " + candidateName;

            notificationService.notify(
                    interview.getManager().getId(),
                    NotificationType.IA,
                    text,
                    "/manager/interviews/" + interview.getId()
            );
        } catch (Exception e) {
            log.error("Error in handleInterviewAiQuestionsReady [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInterviewFeedbackSubmitted(InterviewFeedbackSubmittedEvent event) {
        try {
            if (event.interviewId() == null) return;
            Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
            if (interview == null) return;

            Candidate candidate = interview.getCandidate();
            String candidateName = candidate != null ? candidate.getFirstName() + " " + candidate.getLastName() : "Candidat";
            String managerName = interview.getManager() != null ? interview.getManager().getFirstName() + " " + interview.getManager().getLastName() : "Le manager";

            String text = "Évaluation d'entretien soumise par " + managerName + " pour " + candidateName;

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.EVALUATION,
                    text,
                    "/recruitment/interviews/" + interview.getId(),
                    event.managerId()
            );
        } catch (Exception e) {
            log.error("Error in handleInterviewFeedbackSubmitted [interviewId={}]: {}", event.interviewId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCandidateDecisionMade(CandidateDecisionMadeEvent event) {
        try {
            if (event.candidateId() == null) return;
            Candidate candidate = candidateRepository.findById(event.candidateId()).orElse(null);
            if (candidate == null) return;

            String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
            String status = event.status() != null ? event.status() : candidate.getStatus() != null ? candidate.getStatus().name() : "";

            String text = "Décision finale pour " + candidateName + " : " + status;

            if (candidate.getAssignedManager() != null && !candidate.getAssignedManager().getId().equals(event.actorEmployeeId())) {
                notificationService.notify(
                        candidate.getAssignedManager().getId(),
                        NotificationType.CANDIDATURE,
                        text,
                        "/manager/recruitment/" + candidate.getId()
                );
            }
        } catch (Exception e) {
            log.error("Error in handleCandidateDecisionMade [candidateId={}]: {}", event.candidateId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCandidateStatusChangedByManager(CandidateStatusChangedByManagerEvent event) {
        try {
            if (event.candidateId() == null) return;
            Candidate candidate = candidateRepository.findById(event.candidateId()).orElse(null);
            if (candidate == null) return;

            String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
            String text = "Statut de la candidature " + candidateName + " mis à jour : " + event.newStatus();

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH),
                    NotificationType.CANDIDATURE,
                    text,
                    "/recruitment/candidates/" + candidate.getId(),
                    event.managerId()
            );
        } catch (Exception e) {
            log.error("Error in handleCandidateStatusChangedByManager [candidateId={}]: {}", event.candidateId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleLeaveRequestSubmitted(LeaveRequestSubmittedEvent event) {
        try {
            if (event.leaveId() == null) return;

            Leave leave = leaveRepository.findById(event.leaveId()).orElse(null);
            if (leave == null) return;

            Employee employee = employeeRepository.findById(leave.getEmployeeId()).orElse(null);
            String employeeName = employee != null ? employee.getFirstName() + " " + employee.getLastName() : "Un collaborateur";

            String textManager = "Nouvelle demande de congé à valider pour " + employeeName;

            // Notify manager
            if (event.managerId() != null) {
                notificationService.notify(
                        event.managerId(),
                        NotificationType.LEAVE,
                        textManager,
                        "/manager/leaves"
                );
            }

            // Notify RH
            String textRh = "Nouvelle demande de congé soumise par " + employeeName;
            notificationService.notifyRoles(
                    Set.of(Role.RH),
                    NotificationType.LEAVE,
                    textRh,
                    "/rh/leaves",
                    null
            );
        } catch (Exception e) {
            log.error("Error in handleLeaveRequestSubmitted [leaveId={}]: {}", event.leaveId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleLeaveStatusChanged(LeaveStatusChangedEvent event) {
        try {
            if (event.leaveId() == null) return;
            Leave leave = leaveRepository.findById(event.leaveId()).orElse(null);
            if (leave == null) return;

            Employee employee = employeeRepository.findById(leave.getEmployeeId()).orElse(null);
            String employeeName = employee != null ? employee.getFirstName() + " " + employee.getLastName() : "Collaborateur";

            String statusLabel = event.newStatus();

            // Notify employee
            notificationService.notify(
                    leave.getEmployeeId(),
                    NotificationType.LEAVE,
                    "Votre demande de congé a été " + statusLabel,
                    "/employee/leaves"
            );

            // If validated by manager, notify RH
            if (employee != null && employee.getManager() != null && employee.getManager().getId().equals(event.actorEmployeeId())) {
                notificationService.notifyRoles(
                        Set.of(Role.RH),
                        NotificationType.LEAVE,
                        "Demande de congé de " + employeeName + " validée par son manager",
                        "/rh/leaves",
                        null
                );
            }
        } catch (Exception e) {
            log.error("Error in handleLeaveStatusChanged [leaveId={}]: {}", event.leaveId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTrainingAssigned(TrainingAssignedEvent event) {
        try {
            if (event.employeeId() == null) return;
            Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
            if (employee == null) return;

            String employeeName = employee.getFirstName() + " " + employee.getLastName();

            // Notify employee
            notificationService.notify(
                    employee.getId(),
                    NotificationType.TRAINING,
                    "Une nouvelle formation vous a été assignée",
                    "/employee/trainings"
            );

            // Notify manager if exists and not actor
            if (employee.getManager() != null && !employee.getManager().getId().equals(event.actorEmployeeId())) {
                notificationService.notify(
                        employee.getManager().getId(),
                        NotificationType.TRAINING,
                        "Une formation a été assignée à " + employeeName,
                        "/manager/team"
                );
            }
        } catch (Exception e) {
            log.error("Error in handleTrainingAssigned [employeeId={}]: {}", event.employeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTrainingCompleted(TrainingCompletedEvent event) {
        try {
            if (event.employeeId() == null) return;
            Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
            if (employee == null) return;

            String employeeName = employee.getFirstName() + " " + employee.getLastName();

            if (employee.getManager() != null) {
                notificationService.notify(
                        employee.getManager().getId(),
                        NotificationType.TRAINING,
                        employeeName + " a terminé sa formation",
                        "/manager/team"
                );
            }
        } catch (Exception e) {
            log.error("Error in handleTrainingCompleted [employeeId={}]: {}", event.employeeId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleJobOfferPublished(JobOfferPublishedEvent event) {
        try {
            String title = event.title() != null ? event.title() : "Offre d'emploi";
            String text = "Nouvelle offre d'emploi publiée : " + title;

            notificationService.notifyRoles(
                    Set.of(Role.ADMIN, Role.RH, Role.MANAGER),
                    NotificationType.CANDIDATURE,
                    text,
                    "/recruitment/offers/" + event.jobOfferId(),
                    null
            );
        } catch (Exception e) {
            log.error("Error in handleJobOfferPublished [jobOfferId={}]: {}", event.jobOfferId(), e.getMessage(), e);
        }
    }
}

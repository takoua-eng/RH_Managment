package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.entity.Leave;


import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.entity.Role;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Décision finale sur une demande de congé.
 * Circuit : l'employé demande (PENDING) → son manager accepte (APPROVED) ou refuse (REJECTED).
 * Le RH ne valide pas : il consulte seulement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveDecisionService {

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    @Transactional
    public Leave decide(Long leaveId, boolean approve, String comment, Authentication authentication) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de congé introuvable"));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette demande a déjà été traitée");
        }

        Employee employee = employeeRepository.findById(leave.getEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employé introuvable"));

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
        Employee caller = employeeRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Utilisateur inconnu"));

        // Qui a le droit de décider : le manager de l'employé,
        // ou un ADMIN si l'employé n'a pas de manager
        boolean estSonManager = employee.getManager() != null
                && employee.getManager().getId().equals(caller.getId());
        boolean estAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!estSonManager && !(employee.getManager() == null && estAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le manager de l'employé peut accepter ou refuser cette demande");
        }

        if (approve) {
            if (consommeLeSolde(leave)) {
                int jours = (int) ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
                int solde = employee.getAvailableLeaveDays() == null ? 0 : employee.getAvailableLeaveDays();
                if (jours > solde) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Solde insuffisant : " + solde + " jour(s) disponible(s), " + jours + " demandé(s)");
                }
                employee.setAvailableLeaveDays(solde - jours);
                employeeRepository.save(employee);
            }
            leave.setStatus(LeaveStatus.APPROVED);
        } else {
            if (comment == null || comment.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif du refus est obligatoire");
            }
            leave.setStatus(LeaveStatus.REJECTED);
        }

        leave.setDecidedById(caller.getId());
        leave.setDecidedAt(LocalDateTime.now());
        leave.setDecisionComment(comment);
        Leave saved = leaveRepository.save(leave);

        notifier(saved, employee, caller, approve, comment);
        return saved;
    }

    /** Seul le congé annuel décompte le solde (les congés maladie et autres non). */
    private boolean consommeLeSolde(Leave leave) {
        return leave.getType() == LeaveType.ANNUAL;
    }

    /** Notifie l'employé de la décision, et les RH/ADMIN pour information. */
    private void notifier(Leave leave, Employee employee, Employee caller, boolean approve, String comment) {
        try {
            String periode = "du " + leave.getStartDate() + " au " + leave.getEndDate();
            String managerNom = caller.getFirstName() + " " + caller.getLastName();
            String employeNom = employee.getFirstName() + " " + employee.getLastName();

            String texteEmploye = "Votre demande de congé " + periode + " a été "
                    + (approve ? "acceptée" : "refusée") + " par " + managerNom
                    + (approve ? "." : " : " + comment);
            notificationService.notifyManager(employee.getId(), texteEmploye, NotificationType.LEAVE);

            String texteRh = managerNom + " a " + (approve ? "accepté" : "refusé")
                    + " le congé de " + employeNom + " (" + periode + ")";
            for (Employee rh : employeeRepository.findByRoleIn(List.of(Role.RH, Role.ADMIN))) {
                if (!rh.getId().equals(caller.getId())) {
                    notificationService.notifyManager(rh.getId(), texteRh, NotificationType.LEAVE);
                }
            }
        } catch (Exception e) {
            log.error("Notification de décision de congé impossible : {}", e.getMessage());
        }
    }
}
package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.EmployeeDashboardDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeDashboardDTO.*;
import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import com.esprit.microservice.hrbackend.repository.NotificationRepository;
import com.esprit.microservice.hrbackend.repository.TrainingEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tableau de bord de l'employé connecté.
 * Pas de @Transactional : chaque section est indépendante, et une section en erreur
 * renvoie une valeur vide au lieu de faire échouer toute la page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeDashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final NotificationRepository notificationRepository;

    public EmployeeDashboardDTO getDashboard(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jeton invalide");
        }
        Employee me = employeeRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune fiche employé n'est associée à ce compte"));

        LocalDate today = LocalDate.now();
        List<Leave> myLeaves = safe("congés", () -> leaveRepository.findByEmployeeId(me.getId()), List.of());

        return new EmployeeDashboardDTO(
                safe("profil", () -> profile(me, today), null),
                safe("état des congés", () -> leaveStatus(myLeaves, today), null),
                safe("demandes récentes", () -> recentLeaves(myLeaves), List.of()),
                safe("manager", () -> manager(me), null),
                safe("équipe", () -> teamAbsences(me, today), List.of()),
                safe("formations", () -> trainings(me), new TrainingsInfo(List.of(), 0)),
                safe("mois", () -> monthLeaves(myLeaves, today), List.of()),
                safe("notifications", () -> notifications(me), List.of())
        );
    }

    // ---------------------------------------------------------------- sections

    private Profile profile(Employee me, LocalDate today) {
        return new Profile(
                me.getId(), me.getFirstName(), me.getLastName(), me.getPosition(),
                me.getDepartment() != null ? me.getDepartment().getName() : null,
                me.getHireDate(),
                anciennete(me.getHireDate(), today),
                me.getAvailableLeaveDays()
        );
    }

    private LeaveStatusInfo leaveStatus(List<Leave> leaves, LocalDate today) {
        Optional<Leave> current = leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                .max(Comparator.comparing(Leave::getEndDate));

        Optional<Leave> next = leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> l.getStartDate().isAfter(today))
                .min(Comparator.comparing(Leave::getStartDate));

        long pending = leaves.stream().filter(l -> l.getStatus() == LeaveStatus.PENDING).count();

        // Jours de congé annuel approuvés sur l'année civile en cours
        LocalDate debutAnnee = today.withDayOfYear(1);
        LocalDate finAnnee = today.withDayOfYear(today.lengthOfYear());
        long taken = leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED && l.getType() == LeaveType.ANNUAL)
                .mapToLong(l -> joursDansPeriode(l, debutAnnee, finAnnee))
                .sum();

        Map<Long, String> noms = nomsDesDecideurs(leaves);
        return new LeaveStatusInfo(
                current.isPresent(),
                current.map(Leave::getEndDate).orElse(null),
                next.map(l -> toItem(l, noms)).orElse(null),
                next.map(l -> ChronoUnit.DAYS.between(today, l.getStartDate())).orElse(null),
                pending,
                taken
        );
    }

    private List<LeaveItem> recentLeaves(List<Leave> leaves) {
        Map<Long, String> noms = nomsDesDecideurs(leaves);
        return leaves.stream()
                .sorted(Comparator.comparing(Leave::getStartDate).reversed())
                .limit(6)
                .map(l -> toItem(l, noms))
                .toList();
    }

    private ManagerInfo manager(Employee me) {
        Employee m = me.getManager();
        if (m == null) {
            return null;
        }
        return new ManagerInfo(m.getId(), m.getFirstName(), m.getLastName(), m.getPosition(), m.getEmail());
    }

    /** Collègues ayant le même manager, en congé approuvé aujourd'hui ou dans les 7 prochains jours. */
    private List<TeamAbsence> teamAbsences(Employee me, LocalDate today) {
        if (me.getManager() == null) {
            return List.of();
        }
        Map<Long, Employee> collegues = employeeRepository.findByManagerId(me.getManager().getId()).stream()
                .filter(e -> !e.getId().equals(me.getId()))
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        if (collegues.isEmpty()) {
            return List.of();
        }

        LocalDate horizon = today.plusDays(7);
        return leaveRepository.findByEmployeeIdIn(collegues.keySet()).stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> !l.getEndDate().isBefore(today) && !l.getStartDate().isAfter(horizon))
                .sorted(Comparator.comparing(Leave::getStartDate))
                .map(l -> {
                    Employee e = collegues.get(l.getEmployeeId());
                    boolean aujourdHui = !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today);
                    return new TeamAbsence(e.getFirstName() + " " + e.getLastName(),
                            l.getStartDate(), l.getEndDate(), aujourdHui);
                })
                .toList();
    }

    private TrainingsInfo trainings(Employee me) {
        List<TrainingEnrollment> inscriptions = trainingEnrollmentRepository.findByEmployee_Id(me.getId());

        List<TrainingItem> enCours = inscriptions.stream()
                .filter(t -> !estTerminee(t) && !"Annulée".equalsIgnoreCase(t.getStatus()))
                .map(t -> new TrainingItem(t.getTraining().getTitle(), t.getProgression()))
                .toList();

        long terminees = inscriptions.stream().filter(this::estTerminee).count();
        return new TrainingsInfo(enCours, terminees);
    }

    /** Congés approuvés ou en attente qui touchent le mois en cours (mini-calendrier). */
    private List<MonthLeave> monthLeaves(List<Leave> leaves, LocalDate today) {
        YearMonth mois = YearMonth.from(today);
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        return leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.PENDING)
                .filter(l -> !l.getEndDate().isBefore(debut) && !l.getStartDate().isAfter(fin))
                .map(l -> new MonthLeave(l.getStartDate(), l.getEndDate(), l.getStatus().name()))
                .toList();
    }

    private List<NotificationItem> notifications(Employee me) {
        return notificationRepository.findTop5ByRecipientIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(n -> new NotificationItem(
                        n.getId(),
                        n.getText(),
                        n.getType() != null ? String.valueOf(n.getType()) : null,
                        n.isUnread(),   // ⚠️ isUnread() si le champ est un boolean primitif
                        n.getCreatedAt()))
                .toList();
    }

    // ---------------------------------------------------------------- utilitaires

    private LeaveItem toItem(Leave l, Map<Long, String> noms) {
        return new LeaveItem(
                l.getId(),
                String.valueOf(l.getType()),
                l.getStartDate(),
                l.getEndDate(),
                ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1,
                l.getStatus().name(),
                l.getReason(),
                l.getDecisionComment(),
                l.getDecidedAt(),
                l.getDecidedById() != null ? noms.get(l.getDecidedById()) : null
        );
    }

    /** Noms des managers qui ont décidé, chargés en une seule requête. */
    private Map<Long, String> nomsDesDecideurs(List<Leave> leaves) {
        Set<Long> ids = leaves.stream()
                .map(Leave::getDecidedById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e.getFirstName() + " " + e.getLastName()));
    }

    private long joursDansPeriode(Leave l, LocalDate debut, LocalDate fin) {
        LocalDate s = l.getStartDate().isBefore(debut) ? debut : l.getStartDate();
        LocalDate e = l.getEndDate().isAfter(fin) ? fin : l.getEndDate();
        return e.isBefore(s) ? 0 : ChronoUnit.DAYS.between(s, e) + 1;
    }

    private boolean estTerminee(TrainingEnrollment t) {
        return (t.getProgression() != null && t.getProgression() >= 100)
                || "Terminée".equalsIgnoreCase(t.getStatus());
    }

    private String anciennete(LocalDate embauche, LocalDate today) {
        if (embauche == null || embauche.isAfter(today)) {
            return null;
        }
        Period p = Period.between(embauche, today);
        List<String> parties = new ArrayList<>();
        if (p.getYears() > 0) parties.add(p.getYears() + (p.getYears() > 1 ? " ans" : " an"));
        if (p.getMonths() > 0) parties.add(p.getMonths() + " mois");
        return parties.isEmpty() ? "Moins d'un mois" : String.join(" et ", parties);
    }

    private <T> T safe(String section, Supplier<T> calcul, T valeurParDefaut) {
        try {
            return calcul.get();
        } catch (Exception e) {
            log.warn("Tableau de bord employé – section « {} » en erreur : {}", section, e.getMessage());
            return valeurParDefaut;
        }
    }
}
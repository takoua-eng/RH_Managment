package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.exception.InvalidLeaveStatusTransitionException;
import com.esprit.microservice.hrbackend.exception.LeaveNotFoundException;
import com.esprit.microservice.hrbackend.mapper.LeaveMapper;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import com.esprit.microservice.hrbackend.event.LeaveStatusChangedEvent;

@Service
@RequiredArgsConstructor
public class ManagerLeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;


    // =========================================================
    // LISTE DES CONGÉS DE L'ÉQUIPE
    // =========================================================

    /**
     * Retourne les congés des membres de l'équipe du manager.
     * Filtre optionnel par statut (null = tous les statuts).
     *
     * Workflow attendu :
     *  - APPROVED_RH  → prêts pour validation Manager
     *  - APPROVED_MANAGER, REJECTED, PENDING → historique
     */
    @Transactional(readOnly = true)
    public List<LeaveDTO> getTeamLeaves(
            Long managerId,
            LeaveStatus statusFilter,
            Authentication authentication) {

        verifyManagerAccess(managerId, authentication);

        List<Long> teamIds = getTeamMemberIds(managerId);
        if (teamIds.isEmpty()) {
            return List.of();
        }

        List<Leave> leaves = (statusFilter != null)
                ? leaveRepository.findByEmployeeIdInAndStatus(teamIds, statusFilter)
                : leaveRepository.findByEmployeeIdIn(teamIds);

        return leaves.stream()
                .map(this::toDTO)
                .sorted((a, b) -> {
                    // Trier : PENDING en premier (à valider), puis par date de création desc
                    if (a.getStatus() == LeaveStatus.PENDING && b.getStatus() != LeaveStatus.PENDING) return -1;
                    if (b.getStatus() == LeaveStatus.PENDING && a.getStatus() != LeaveStatus.PENDING) return 1;
                    if (b.getCreatedAt() != null && a.getCreatedAt() != null)
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    return 0;
                })
                .collect(Collectors.toList());
    }


    // =========================================================
    // VALIDATION MANAGER (APPROUVER)
    // =========================================================

    /**
     * Le manager approuve un congé.
     * Workflow : PENDING → APPROVED_MANAGER
     */
    @Transactional
    public LeaveDTO approveByManager(
            Long managerId,
            Long leaveId,
            Authentication authentication) {

        verifyManagerAccess(managerId, authentication);

        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Congé introuvable avec l'id : " + leaveId));

        // Vérification appartenance à l'équipe
        verifyLeaveOwnership(managerId, leave);

        // Workflow : doit être PENDING pour être validé par le Manager
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidLeaveStatusTransitionException(
                    "Ce congé doit être En attente (PENDING) pour être validé par le Manager. " +
                    "Statut actuel : " + leave.getStatus()
            );
        }

        leave.setStatus(LeaveStatus.APPROVED);
        
        // La déduction se fait dès la validation du Manager selon votre demande
        Employee employee = employeeRepository.findById(leave.getEmployeeId())
                .orElseThrow(() -> new AccessDeniedException("Employé introuvable."));
        
        long days = java.time.temporal.ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
        
        int currentBalance = employee.getAvailableLeaveDays() != null ? employee.getAvailableLeaveDays() : 0;
        int newBalance = currentBalance - (int) days;
        if (newBalance < 0) {
            throw new IllegalArgumentException("Solde de congés insuffisant pour cet employé.");
        }
        
        employee.setAvailableLeaveDays(newBalance);
        employeeRepository.save(employee);

        Leave updated = leaveRepository.save(leave);
        
        eventPublisher.publishEvent(new LeaveStatusChangedEvent(updated.getId(), "VALIDÉE PAR MANAGER", managerId));
        
        return toDTO(updated);
    }



    // =========================================================
    // VALIDATION MANAGER (REFUSER)
    // =========================================================

    /**
     * Le manager refuse un congé (APPROVED_RH ou PENDING).
     * Un congé déjà REJECTED ne peut pas être re-rejeté.
     */
    @Transactional
    public LeaveDTO rejectByManager(
            Long managerId,
            Long leaveId,
            String reason,
            Authentication authentication) {

        verifyManagerAccess(managerId, authentication);

        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Congé introuvable avec l'id : " + leaveId));

        // Vérification appartenance à l'équipe
        verifyLeaveOwnership(managerId, leave);

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new InvalidLeaveStatusTransitionException("Ce congé est déjà refusé.");
        }

        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new InvalidLeaveStatusTransitionException("Ce congé a déjà été approuvé définitivement.");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setReason(reason != null ? reason : "Refusé par le manager");
        Leave updated = leaveRepository.save(leave);
        
        eventPublisher.publishEvent(new LeaveStatusChangedEvent(updated.getId(), "REFUSÉE PAR MANAGER", managerId));
        
        return toDTO(updated);
    }


    // =========================================================
    // UTILITAIRES PRIVÉS
    // =========================================================

    /** Vérifie que le caller est bien le manager du path (ou ADMIN) */
    private void verifyManagerAccess(Long managerId, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("JWT invalide.");
        }

        String keycloakId = jwt.getSubject();
        Employee caller = employeeRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new AccessDeniedException("Aucun employé trouvé pour ce token."));

        if (!caller.getId().equals(managerId)) {
            throw new AccessDeniedException(
                    "Vous n'êtes pas autorisé à gérer les congés du manager " + managerId
            );
        }
    }

    /** Retourne les IDs des membres de l'équipe */
    private List<Long> getTeamMemberIds(Long managerId) {
        return employeeRepository.findByManagerId(managerId)
                .stream()
                .map(Employee::getId)
                .collect(Collectors.toList());
    }

    /**
     * Vérifie que le congé appartient bien à un membre de l'équipe du manager.
     * Extrait l'employé depuis la base par son ID stocké sur le congé.
     */
    private void verifyLeaveOwnership(Long managerId, Leave leave) {
        Set<Long> teamIds = getTeamMemberIds(managerId).stream().collect(Collectors.toSet());

        if (!teamIds.contains(leave.getEmployeeId())) {
            throw new AccessDeniedException(
                    "Ce congé n'appartient pas à un membre de votre équipe."
            );
        }
    }

    /** Convertit en DTO avec le nom de l'employé */
    private LeaveDTO toDTO(Leave leave) {
        LeaveDTO dto = LeaveMapper.toDTO(leave);
        if (dto != null) {
            String name = employeeRepository.findById(leave.getEmployeeId())
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse("Employé inconnu");
            dto.setEmployeeName(name);
        }
        return dto;
    }
}

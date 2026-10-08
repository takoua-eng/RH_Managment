package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.LeaveCalendarDTO;
import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.dto.LeaveStatsDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.event.LeaveRequestSubmittedEvent;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidLeaveStatusTransitionException;
import com.esprit.microservice.hrbackend.exception.LeaveNotFoundException;
import com.esprit.microservice.hrbackend.mapper.LeaveMapper;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Demandes, consultation et annulation des congés.
 * La décision (acceptation ou refus) est prise par le manager, dans LeaveDecisionService.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    // =========================================================
    // DEMANDE DE CONGÉ
    // =========================================================

    /** Crée une demande au statut PENDING et prévient le manager de l'employé. */
    @Transactional
    public LeaveDTO requestLeave(LeaveDTO dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + dto.getEmployeeId()));

        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new InvalidLeaveStatusTransitionException("Start date and end date are mandatory");
        }

        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new InvalidLeaveStatusTransitionException("Start date must be before or equal to end date");
        }

        Leave leave = LeaveMapper.toEntity(dto);
        leave.setStatus(LeaveStatus.PENDING);

        Leave saved = leaveRepository.save(leave);

        // Notification du manager par événement (envoyée après la validation de la transaction)
        if (employee.getManager() != null) {
            eventPublisher.publishEvent(new LeaveRequestSubmittedEvent(saved.getId(), employee.getManager().getId()));
        }

        return convertToDTOWithEmployeeName(saved);
    }

    // =========================================================
    // CONSULTATION
    // =========================================================

    @Transactional(readOnly = true)
    public List<LeaveDTO> getLeaveHistory(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException("Employee not found with id: " + employeeId);
        }

        return leaveRepository.findByEmployeeId(employeeId).stream()
                .map(this::convertToDTOWithEmployeeName)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveDTO> getAllLeaves() {
        return leaveRepository.findAll().stream()
                .map(this::convertToDTOWithEmployeeName)
                .collect(Collectors.toList());
    }

    /** Demandes en attente de la décision du manager. */
    @Transactional(readOnly = true)
    public List<LeaveDTO> getPendingLeaves() {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.PENDING)
                .map(this::convertToDTOWithEmployeeName)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveStatsDTO getStats() {
        List<Leave> allLeaves = leaveRepository.findAll();
        long totalEmployees = employeeRepository.count();

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();

        // En attente : uniquement les demandes que le manager n'a pas encore traitées
        long pending = allLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.PENDING)
                .count();

        // Absents aujourd'hui : congés approuvés couvrant la date du jour (un employé compté une fois)
        long absentToday = allLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                .map(Leave::getEmployeeId)
                .distinct()
                .count();

        // Approuvés ce mois-ci
        long approvedThisMonth = allLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> (l.getCreatedAt() != null && YearMonth.from(l.getCreatedAt()).equals(thisMonth))
                        || (l.getStartDate() != null && YearMonth.from(l.getStartDate()).equals(thisMonth)))
                .count();

        double presenceRate = 100.0;
        if (totalEmployees > 0) {
            presenceRate = 100.0 - (((double) absentToday / totalEmployees) * 100.0);
            presenceRate = Math.round(presenceRate * 10.0) / 10.0;
        }

        return LeaveStatsDTO.builder()
                .pendingCount(pending)
                .absentToday(absentToday)
                .approvedThisMonth(approvedThisMonth)
                .presenceRate(presenceRate)
                .build();
    }

    /** Absences du mois : congés approuvés et en attente (les refusés et annulés sont exclus). */
    @Transactional(readOnly = true)
    public List<LeaveCalendarDTO> getCalendarAbsences(int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        List<Leave> leaves = leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.PENDING)
                .filter(l -> !l.getStartDate().isAfter(endOfMonth) && !l.getEndDate().isBefore(startOfMonth))
                .collect(Collectors.toList());

        List<LeaveCalendarDTO> calendarAbsences = new ArrayList<>();

        for (Leave l : leaves) {
            String employeeName = employeeRepository.findById(l.getEmployeeId())
                    .map(emp -> emp.getFirstName() + " " + emp.getLastName())
                    .orElse("Unknown Employee");

            List<String> dates = new ArrayList<>();
            LocalDate current = l.getStartDate().isBefore(startOfMonth) ? startOfMonth : l.getStartDate();
            LocalDate end = l.getEndDate().isAfter(endOfMonth) ? endOfMonth : l.getEndDate();

            while (!current.isAfter(end)) {
                dates.add(current.toString());
                current = current.plusDays(1);
            }

            String frontendType = "PAID";
            if (l.getType() == LeaveType.SICK) {
                frontendType = "SICK";
            } else if (l.getType() == LeaveType.OTHER) {
                frontendType = "RTT";
            }

            long daysCount = ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1;

            calendarAbsences.add(LeaveCalendarDTO.builder()
                    .id(l.getId())
                    .employeeId(l.getEmployeeId())
                    .employeeName(employeeName)
                    .dates(dates)
                    .type(frontendType)
                    .startDate(l.getStartDate())
                    .endDate(l.getEndDate())
                    .daysCount(daysCount)
                    .status(l.getStatus().name())
                    .reason(l.getReason())
                    .build());
        }

        return calendarAbsences;
    }

    // =========================================================
    // ANNULATION PAR L'EMPLOYÉ
    // =========================================================

    /** Seule une demande en attente peut être annulée, et uniquement par son auteur (ou un ADMIN/RH/MANAGER). */
    @Transactional
    public void cancelLeave(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with id: " + leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidLeaveStatusTransitionException("Only pending leave requests can be cancelled");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isEmployeeOnly = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_RH") || auth.equals("ROLE_MANAGER"));

        if (isEmployeeOnly && authentication.getPrincipal() instanceof Jwt jwt) {
            Employee employee = employeeRepository.findById(leave.getEmployeeId())
                    .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + leave.getEmployeeId()));
            if (employee.getKeycloakId() == null || !employee.getKeycloakId().equals(jwt.getSubject())) {
                throw new AccessDeniedException("You are not authorized to cancel this leave request");
            }
        }

        leaveRepository.delete(leave);
    }

    // =========================================================
    // UTILITAIRE
    // =========================================================

    private LeaveDTO convertToDTOWithEmployeeName(Leave leave) {
        LeaveDTO dto = LeaveMapper.toDTO(leave);
        if (dto != null) {
            String employeeName = employeeRepository.findById(leave.getEmployeeId())
                    .map(emp -> emp.getFirstName() + " " + emp.getLastName())
                    .orElse("Unknown Employee");
            dto.setEmployeeName(employeeName);
        }
        return dto;
    }
}
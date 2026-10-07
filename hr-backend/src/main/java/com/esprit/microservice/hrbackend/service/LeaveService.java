package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.LeaveCalendarDTO;
import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.dto.LeaveStatsDTO;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidLeaveStatusTransitionException;
import com.esprit.microservice.hrbackend.exception.LeaveNotFoundException;
import com.esprit.microservice.hrbackend.mapper.LeaveMapper;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.service.NotificationService;

import org.springframework.context.ApplicationEventPublisher;
import com.esprit.microservice.hrbackend.event.LeaveRequestSubmittedEvent;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LeaveDTO requestLeave(LeaveDTO dto) {
        var employee = employeeRepository.findById(dto.getEmployeeId())
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
        
        // Notify Manager via Domain Event
        if (employee.getManager() != null) {
            eventPublisher.publishEvent(new LeaveRequestSubmittedEvent(saved.getId(), employee.getManager().getId()));
        }

        return convertToDTOWithEmployeeName(saved);
    }

    @Transactional
    public LeaveDTO approveByRH(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with id: " + leaveId));

        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new InvalidLeaveStatusTransitionException("Leave request is already approved by RH");
        }

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new InvalidLeaveStatusTransitionException("Cannot approve a rejected leave request");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        Leave updated = leaveRepository.save(leave);
        
        eventPublisher.publishEvent(new com.esprit.microservice.hrbackend.event.LeaveStatusChangedEvent(updated.getId(), "ACCEPTÉE PAR LES RH", null));
        
        return convertToDTOWithEmployeeName(updated);
    }

    @Transactional
    public LeaveDTO approveByManager(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with id: " + leaveId));

        if (leave.getStatus() == LeaveStatus.APPROVED) {
            throw new InvalidLeaveStatusTransitionException("Leave request is already approved by Manager");
        }

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new InvalidLeaveStatusTransitionException("Cannot approve a rejected leave request");
        }

        leave.setStatus(LeaveStatus.APPROVED);
        Leave updated = leaveRepository.save(leave);

        eventPublisher.publishEvent(new com.esprit.microservice.hrbackend.event.LeaveStatusChangedEvent(updated.getId(), "VALIDÉE PAR LE MANAGER", null));

        return convertToDTOWithEmployeeName(updated);
    }

    @Transactional
    public LeaveDTO rejectLeave(Long leaveId, String reason) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with id: " + leaveId));

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new InvalidLeaveStatusTransitionException("Leave request is already rejected");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setReason(reason);
        Leave updated = leaveRepository.save(leave);
        
        eventPublisher.publishEvent(new com.esprit.microservice.hrbackend.event.LeaveStatusChangedEvent(updated.getId(), "REFUSÉE", null));
        
        return convertToDTOWithEmployeeName(updated);
    }

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

    @Transactional(readOnly = true)
    public LeaveStatsDTO getStats() {
        List<Leave> allLeaves = leaveRepository.findAll();
        long totalEmployees = employeeRepository.count();

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();

        long pending = allLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED|| l.getStatus() == LeaveStatus.PENDING)
                .count();

        long absentToday = allLeaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.APPROVED))
                .filter(l -> !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                .map(Leave::getEmployeeId)
                .distinct()
                .count();

        long approvedThisMonth = allLeaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.APPROVED))
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

    @Transactional(readOnly = true)
    public List<LeaveCalendarDTO> getCalendarAbsences(int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        List<Leave> approvedLeaves = leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() != LeaveStatus.REJECTED)
                .filter(l -> !l.getStartDate().isAfter(endOfMonth) && !l.getEndDate().isBefore(startOfMonth))
                .collect(Collectors.toList());

        List<LeaveCalendarDTO> calendarAbsences = new ArrayList<>();

        for (Leave l : approvedLeaves) {
            String employeeName = employeeRepository.findById(l.getEmployeeId())
                    .map(emp -> emp.getFirstName() + " " + emp.getLastName())
                    .orElse("Unknown Employee");

            List<String> dates = new ArrayList<>();
            LocalDate current = l.getStartDate();
            if (current.isBefore(startOfMonth)) {
                current = startOfMonth;
            }
            LocalDate end = l.getEndDate();
            if (end.isAfter(endOfMonth)) {
                end = endOfMonth;
            }

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

            long daysCount = java.time.temporal.ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1;

            calendarAbsences.add(LeaveCalendarDTO.builder()
                    .id(l.getId())
                    .employeeId(l.getEmployeeId())
                    .employeeName(employeeName)
                    .dates(dates)
                    .type(frontendType)
                    .startDate(l.getStartDate())
                    .endDate(l.getEndDate())
                    .daysCount(daysCount)
                    .status(l.getStatus() != null ? l.getStatus().name() : "APPROVED_RH")
                    .reason(l.getReason())
                    .build());
        }

        return calendarAbsences;
    }

    @Transactional(readOnly = true)
    public List<LeaveDTO> getPendingLeaves() {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED || l.getStatus() == LeaveStatus.PENDING)
                .map(this::convertToDTOWithEmployeeName)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelLeave(Long leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with id: " + leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidLeaveStatusTransitionException("Only pending leave requests can be cancelled");
        }

        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isEmployeeOnly = authentication != null && authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .noneMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_RH") || auth.equals("ROLE_MANAGER"));

        if (isEmployeeOnly && authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt) {
                org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) principal;
                String tokenKeycloakId = jwt.getSubject();

                com.esprit.microservice.hrbackend.entity.Employee employee = employeeRepository.findById(leave.getEmployeeId())
                        .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + leave.getEmployeeId()));
                if (employee.getKeycloakId() == null || !employee.getKeycloakId().equals(tokenKeycloakId)) {
                    throw new org.springframework.security.access.AccessDeniedException("You are not authorized to cancel this leave request");
                }
            }
        }

        leaveRepository.delete(leave);
    }

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

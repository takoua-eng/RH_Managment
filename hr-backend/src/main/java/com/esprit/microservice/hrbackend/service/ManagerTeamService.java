package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.ManagerTeamMemberDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerTeamService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRepository leaveRepository;

    /**
     * Récupère l'équipe du manager connecté identifié par son keycloakId.
     * Règle de sélection :
     * - Exclut le manager lui-même.
     * - Inclut les employés ayant pour manager direct cet employé OU faisant partie du même département.
     * - Exclut strictement tout employé appartenant à un autre département (si le manager a un département).
     */
    public List<ManagerTeamMemberDTO> getManagerTeam(String keycloakId) {
        Employee manager = employeeRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new AccessDeniedException("Manager non trouvé pour ce token."));

        Long managerId = manager.getId();
        List<Employee> team = employeeRepository.findByManagerId(managerId).stream()
                .filter(e -> !e.getId().equals(managerId))
                .sorted(Comparator.comparing(Employee::getFirstName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        if (team.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> teamEmpIds = team.stream().map(Employee::getId).collect(Collectors.toSet());
        LocalDate today = LocalDate.now();

        List<Leave> activeLeaves = leaveRepository.findByEmployeeIdInAndStatusIn(
                teamEmpIds,
                List.of(LeaveStatus.APPROVED, LeaveStatus.APPROVED)
        );

        Set<Long> absentEmpIds = activeLeaves.stream()
                .filter(l -> l.getStartDate() != null && l.getEndDate() != null)
                .filter(l -> !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                .map(Leave::getEmployeeId)
                .collect(Collectors.toSet());

        return team.stream()
                .map(e -> mapToDto(e, absentEmpIds.contains(e.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Récupère le détail d'un membre de l'équipe du manager.
     */
    public ManagerTeamMemberDTO getTeamMemberDetail(String keycloakId, Long employeeId) {
        Employee manager = employeeRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new AccessDeniedException("Manager non trouvé."));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employé introuvable avec l'id : " + employeeId));

        Long managerId = manager.getId();
        boolean isSubordinate = employee.getManager() != null && employee.getManager().getId().equals(managerId) && !employee.getId().equals(managerId);

        if (!isSubordinate) {
            throw new AccessDeniedException("L'employé " + employeeId + " n'appartient pas à votre équipe.");
        }

        LocalDate today = LocalDate.now();
        List<Leave> activeLeaves = leaveRepository.findByEmployeeIdInAndStatusIn(
                Set.of(employeeId),
                List.of(LeaveStatus.APPROVED, LeaveStatus.APPROVED)
        );
        boolean isOnLeaveToday = activeLeaves.stream()
                .anyMatch(l -> l.getStartDate() != null && l.getEndDate() != null && !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today));

        return mapToDto(employee, isOnLeaveToday);
    }

    private ManagerTeamMemberDTO mapToDto(Employee e, boolean isOnLeaveToday) {
        String photoUrl = e.getPhotoPath() != null ? "http://localhost:8087/api/employees/" + e.getId() + "/photo" : null;
        String deptName = e.getDepartment() != null ? e.getDepartment().getName() : null;

        return new ManagerTeamMemberDTO(
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getPhone(),
                e.getPosition(),
                e.getStatus(),
                e.getHireDate(),
                photoUrl,
                deptName,
                isOnLeaveToday
        );
    }
}

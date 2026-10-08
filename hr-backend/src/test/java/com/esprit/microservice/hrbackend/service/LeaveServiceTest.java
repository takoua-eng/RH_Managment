package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.event.LeaveRequestSubmittedEvent;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidLeaveStatusTransitionException;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de LeaveService : demande, consultation et annulation des congés.
 * Les décisions (acceptation, refus) sont testées dans LeaveDecisionServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LeaveService leaveService;

    private Employee manager;
    private Employee employee;
    private LeaveDTO leaveDTO;
    private Leave leave;

    @BeforeEach
    void setUp() {
        manager = Employee.builder().id(24L).firstName("Manager").lastName("Test").build();

        employee = Employee.builder()
                .id(1L).firstName("Employee").lastName("Test")
                .manager(manager)
                .build();

        leaveDTO = LeaveDTO.builder()
                .employeeId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .type(LeaveType.ANNUAL)
                .reason("Vacances")
                .build();

        leave = Leave.builder()
                .id(100L)
                .employeeId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .type(LeaveType.ANNUAL)
                .status(LeaveStatus.PENDING)
                .reason("Vacances")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ---------------------------------------------------------------- demande

    @Test
    @DisplayName("Demande valide : enregistrée en PENDING et manager prévenu")
    void demandeValide() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRepository.save(any(Leave.class))).thenReturn(leave);

        LeaveDTO result = leaveService.requestLeave(leaveDTO);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        assertEquals("Employee Test", result.getEmployeeName());
        verify(leaveRepository).save(any(Leave.class));
        verify(eventPublisher).publishEvent(any(LeaveRequestSubmittedEvent.class));
    }

    @Test
    @DisplayName("Demande d'un employé sans manager : enregistrée, sans notification")
    void demandeSansManager() {
        employee.setManager(null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveRepository.save(any(Leave.class))).thenReturn(leave);

        LeaveDTO result = leaveService.requestLeave(leaveDTO);

        assertEquals(LeaveStatus.PENDING, result.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Employé inconnu : demande refusée")
    void employeInconnu() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EmployeeNotFoundException.class, () -> leaveService.requestLeave(leaveDTO));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    @DisplayName("Date de début après la date de fin : demande refusée")
    void datesInversees() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        leaveDTO.setStartDate(LocalDate.now().plusDays(5));
        leaveDTO.setEndDate(LocalDate.now().plusDays(1));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.requestLeave(leaveDTO));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    @DisplayName("Dates manquantes : demande refusée")
    void datesManquantes() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        leaveDTO.setEndDate(null);

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.requestLeave(leaveDTO));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    // ---------------------------------------------------------------- consultation

    @Test
    @DisplayName("Historique d'un employé")
    void historique() {
        when(employeeRepository.existsById(1L)).thenReturn(true);
        when(leaveRepository.findByEmployeeId(1L)).thenReturn(List.of(leave));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        List<LeaveDTO> history = leaveService.getLeaveHistory(1L);

        assertEquals(1, history.size());
        assertEquals(100L, history.get(0).getId());
        assertEquals("Employee Test", history.get(0).getEmployeeName());
    }

    @Test
    @DisplayName("Historique d'un employé inconnu : erreur")
    void historiqueEmployeInconnu() {
        when(employeeRepository.existsById(1L)).thenReturn(false);

        assertThrows(EmployeeNotFoundException.class, () -> leaveService.getLeaveHistory(1L));
        verify(leaveRepository, never()).findByEmployeeId(any());
    }

    @Test
    @DisplayName("Demandes en attente : les congés déjà approuvés ou refusés sont exclus")
    void demandesEnAttenteUniquement() {
        Leave approuve = Leave.builder().id(101L).employeeId(1L).status(LeaveStatus.APPROVED).build();
        Leave refuse = Leave.builder().id(102L).employeeId(1L).status(LeaveStatus.REJECTED).build();
        when(leaveRepository.findAll()).thenReturn(List.of(leave, approuve, refuse));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        List<LeaveDTO> pending = leaveService.getPendingLeaves();

        assertEquals(1, pending.size());
        assertEquals(100L, pending.get(0).getId());
    }

    // ---------------------------------------------------------------- annulation

    @Test
    @DisplayName("Une demande en attente peut être annulée")
    void annulationDemandeEnAttente() {
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        leaveService.cancelLeave(100L);

        verify(leaveRepository).delete(leave);
    }

    @Test
    @DisplayName("Un congé déjà approuvé ne peut pas être annulé")
    void annulationCongeApprouveInterdite() {
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.cancelLeave(100L));
        verify(leaveRepository, never()).delete(any());
    }
}
package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidLeaveStatusTransitionException;
import com.esprit.microservice.hrbackend.exception.LeaveNotFoundException;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LeaveService leaveService;

    private LeaveDTO leaveDTO;
    private Leave leave;

    @BeforeEach
    void setUp() {
        leaveDTO = LeaveDTO.builder()
                .employeeId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .type(LeaveType.ANNUAL)
                .reason("Vacation")
                .build();

        leave = Leave.builder()
                .id(100L)
                .employeeId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .type(LeaveType.ANNUAL)
                .status(LeaveStatus.PENDING)
                .reason("Vacation")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testRequestLeave_Success() {
        when(employeeRepository.existsById(1L)).thenReturn(true);
        when(leaveRepository.save(any(Leave.class))).thenReturn(leave);

        LeaveDTO result = leaveService.requestLeave(leaveDTO);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getEmployeeId());
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        verify(employeeRepository, times(1)).existsById(1L);
        verify(leaveRepository, times(1)).save(any(Leave.class));
    }

    @Test
    void testRequestLeave_EmployeeNotFound() {
        when(employeeRepository.existsById(1L)).thenReturn(false);

        assertThrows(EmployeeNotFoundException.class, () -> leaveService.requestLeave(leaveDTO));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testRequestLeave_InvalidDates() {
        when(employeeRepository.existsById(1L)).thenReturn(true);
        leaveDTO.setStartDate(LocalDate.now().plusDays(5));
        leaveDTO.setEndDate(LocalDate.now().plusDays(1));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.requestLeave(leaveDTO));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testApproveByRH_Success() {
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveDTO result = leaveService.approveByRH(100L);

        assertNotNull(result);
        assertEquals(LeaveStatus.APPROVED, result.getStatus());
        verify(leaveRepository, times(1)).findById(100L);
        verify(leaveRepository, times(1)).save(any(Leave.class));
    }

    @Test
    void testApproveByRH_AlreadyApproved() {
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.approveByRH(100L));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testApproveByRH_RejectedLeave() {
        leave.setStatus(LeaveStatus.REJECTED);
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.approveByRH(100L));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testApproveByManager_Success() {
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveDTO result = leaveService.approveByManager(100L);

        assertNotNull(result);
        assertEquals(LeaveStatus.APPROVED, result.getStatus());
        verify(leaveRepository, times(1)).findById(100L);
        verify(leaveRepository, times(1)).save(any(Leave.class));
    }

    @Test
    void testApproveByManager_AlreadyApproved() {
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.approveByManager(100L));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testRejectLeave_Success() {
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveDTO result = leaveService.rejectLeave(100L, "Out of budget");

        assertNotNull(result);
        assertEquals(LeaveStatus.REJECTED, result.getStatus());
        assertEquals("Out of budget", result.getReason());
        verify(leaveRepository, times(1)).findById(100L);
        verify(leaveRepository, times(1)).save(any(Leave.class));
    }

    @Test
    void testRejectLeave_AlreadyRejected() {
        leave.setStatus(LeaveStatus.REJECTED);
        when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

        assertThrows(InvalidLeaveStatusTransitionException.class, () -> leaveService.rejectLeave(100L, "Reason"));
        verify(leaveRepository, never()).save(any(Leave.class));
    }

    @Test
    void testGetLeaveHistory_Success() {
        when(employeeRepository.existsById(1L)).thenReturn(true);
        when(leaveRepository.findByEmployeeId(1L)).thenReturn(Arrays.asList(leave));

        List<LeaveDTO> history = leaveService.getLeaveHistory(1L);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(100L, history.get(0).getId());
        verify(employeeRepository, times(1)).existsById(1L);
        verify(leaveRepository, times(1)).findByEmployeeId(1L);
    }
}

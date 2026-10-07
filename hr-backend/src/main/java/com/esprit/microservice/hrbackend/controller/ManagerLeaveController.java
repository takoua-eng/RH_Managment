package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.service.ManagerLeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager/{managerId}/leaves")
@RequiredArgsConstructor
public class ManagerLeaveController {

    private final ManagerLeaveService managerLeaveService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<LeaveDTO>> getTeamLeaves(
            @PathVariable Long managerId,
            @RequestParam(required = false) LeaveStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(managerLeaveService.getTeamLeaves(managerId, status, authentication));
    }

    @PutMapping("/{leaveId}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<LeaveDTO> approveByManager(
            @PathVariable Long managerId,
            @PathVariable Long leaveId,
            Authentication authentication) {
        return ResponseEntity.ok(managerLeaveService.approveByManager(managerId, leaveId, authentication));
    }

    @PutMapping("/{leaveId}/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<LeaveDTO> rejectByManager(
            @PathVariable Long managerId,
            @PathVariable Long leaveId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String reason = (body != null) ? body.get("reason") : null;
        return ResponseEntity.ok(managerLeaveService.rejectByManager(managerId, leaveId, reason, authentication));
    }
}

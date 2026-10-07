package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.service.LeaveDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Décision du manager sur une demande de congé.
 * PUT /api/leave-decisions/{id}/approve   corps optionnel : { "comment": "..." }
 * PUT /api/leave-decisions/{id}/reject    corps obligatoire : { "comment": "motif" }
 */
@RestController
@RequestMapping("/api/leave-decisions")
@RequiredArgsConstructor
public class LeaveDecisionController {

    private final LeaveDecisionService leaveDecisionService;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        return new ResponseEntity<>(Map.of("message", String.valueOf(ex.getReason())), ex.getStatusCode());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Leave> approve(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> body,
                                         Authentication authentication) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(leaveDecisionService.decide(id, true, comment, authentication));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Leave> reject(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, String> body,
                                        Authentication authentication) {
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(leaveDecisionService.decide(id, false, comment, authentication));
    }
}
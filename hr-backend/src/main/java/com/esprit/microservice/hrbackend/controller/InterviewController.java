package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.InterviewFeedbackDTO;
import com.esprit.microservice.hrbackend.dto.InterviewNotesDTO;
import com.esprit.microservice.hrbackend.dto.InterviewRequestDTO;
import com.esprit.microservice.hrbackend.dto.InterviewResponseDTO;
import com.esprit.microservice.hrbackend.entity.InterviewStatus;
import com.esprit.microservice.hrbackend.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> scheduleInterview(
            @Valid @RequestBody InterviewRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.scheduleInterview(dto, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> updateInterview(
            @PathVariable Long id,
            @Valid @RequestBody InterviewRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.updateInterview(id, dto, authentication));
    }

    @GetMapping("/candidate/{candidateId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<List<InterviewResponseDTO>> getInterviewsByCandidate(
            @PathVariable Long candidateId,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.getInterviewsByCandidate(candidateId, authentication));
    }

    @GetMapping("/my-interviews")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<List<InterviewResponseDTO>> getMyInterviews(Authentication authentication) {
        return ResponseEntity.ok(interviewService.getMyInterviews(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> getInterviewById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.getInterviewById(id, authentication));
    }


    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam InterviewStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.updateInterviewStatus(id, status, authentication));
    }

    @PostMapping("/{id}/resend-email")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> resendInterviewEmail(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.resendInterviewEmail(id, authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> cancelInterview(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.cancelInterview(id, reason, authentication));
    }

    @PostMapping("/{id}/questions/regenerate")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<Void> regenerateQuestions(
            @PathVariable Long id,
            Authentication authentication) {
        interviewService.regenerateQuestions(id, authentication);
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> saveNotes(
            @PathVariable Long id,
            @RequestBody InterviewNotesDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.saveNotes(id, dto, authentication));
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<InterviewResponseDTO> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody InterviewFeedbackDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(interviewService.submitFeedback(id, dto, authentication));
    }

}

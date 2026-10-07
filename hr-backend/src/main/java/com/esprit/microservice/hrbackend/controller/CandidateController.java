package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.CandidateRequestDTO;
import com.esprit.microservice.hrbackend.dto.CandidateResponseDTO;
import com.esprit.microservice.hrbackend.entity.Candidate;
import com.esprit.microservice.hrbackend.entity.CandidateStatus;
import com.esprit.microservice.hrbackend.mapper.CandidateMapper;
import com.esprit.microservice.hrbackend.service.CandidateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment/candidates")
@RequiredArgsConstructor
@Slf4j
public class CandidateController {

    private final CandidateService candidateService;
    private final ObjectMapper objectMapper;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // PUBLIC ENDPOINT - No authentication required for submitting an application
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateResponseDTO> apply(
            @RequestPart("candidate") String candidateJson,
            @RequestPart(value = "cv", required = false) MultipartFile cv,
            @RequestPart(value = "motivationLetter", required = false) MultipartFile motivationLetter) {

        // 1. Lecture du JSON : seule cette étape peut produire "Invalid candidate data format"
        CandidateRequestDTO requestDTO;
        try {
            requestDTO = objectMapper.readValue(candidateJson, CandidateRequestDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Données candidat invalides : {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid candidate data format");
        }

        // 2. Traitement métier : les erreurs (offre fermée, date dépassée, doublon, CV manquant...)
        //    remontent avec leur propre message
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateService.apply(requestDTO, cv, motivationLetter));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<List<CandidateResponseDTO>> getAllCandidates() {
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @GetMapping("/assigned-to-me")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'RH')")
    public ResponseEntity<List<CandidateResponseDTO>> getAssignedCandidates(Authentication authentication) {
        return ResponseEntity.ok(candidateService.getCandidatesAssignedToManager(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<CandidateResponseDTO> getCandidateById(@PathVariable Long id, Authentication authentication) {
        Candidate candidate = candidateService.getCandidateEntity(id);
        candidateService.verifyCandidateAccess(candidate, authentication);
        return ResponseEntity.ok(CandidateMapper.toResponseDTO(candidate));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<CandidateResponseDTO> updateCandidate(
            @PathVariable Long id,
            @RequestBody CandidateRequestDTO requestDTO) {
        return ResponseEntity.ok(candidateService.updateCandidate(id, requestDTO));
    }

    @GetMapping("/offer/{offerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<List<CandidateResponseDTO>> getCandidatesByJobOffer(@PathVariable Long offerId) {
        return ResponseEntity.ok(candidateService.getCandidatesByJobOffer(offerId));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<CandidateResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam CandidateStatus status,
            Authentication authentication) {
        Candidate candidate = candidateService.getCandidateEntity(id);
        candidateService.verifyCandidateAccess(candidate, authentication);
        return ResponseEntity.ok(candidateService.updateStatus(id, status));
    }

    @PutMapping("/{id}/transmit")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<CandidateResponseDTO> transmitToManager(
            @PathVariable Long id,
            @RequestParam Long managerId) {
        return ResponseEntity.ok(candidateService.transmitToManager(id, managerId));
    }

    @GetMapping("/{id}/cv")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<org.springframework.core.io.Resource> downloadCv(@PathVariable Long id, Authentication authentication) {
        Candidate candidate = candidateService.getCandidateEntity(id);
        candidateService.verifyCandidateAccess(candidate, authentication);

        org.springframework.core.io.Resource resource = candidateService.getCvResource(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + candidate.getCvFileName() + "\"")
                .contentType(MediaType.parseMediaType(candidate.getCvContentType() != null ? candidate.getCvContentType() : "application/octet-stream"))
                .body(resource);
    }

    @GetMapping("/{id}/motivation-letter")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<org.springframework.core.io.Resource> downloadMotivationLetter(@PathVariable Long id, Authentication authentication) {
        Candidate candidate = candidateService.getCandidateEntity(id);
        candidateService.verifyCandidateAccess(candidate, authentication);

        org.springframework.core.io.Resource resource = candidateService.getMotivationLetterResource(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + candidate.getMotivationLetterFileName() + "\"")
                .contentType(MediaType.parseMediaType(candidate.getMotivationLetterContentType() != null ? candidate.getMotivationLetterContentType() : "application/octet-stream"))
                .body(resource);
    }
}
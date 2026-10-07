package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.JobOfferRequestDTO;
import com.esprit.microservice.hrbackend.dto.JobOfferResponseDTO;
import com.esprit.microservice.hrbackend.service.JobOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment/offers")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<JobOfferResponseDTO> createJobOffer(@RequestBody JobOfferRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobOfferService.createJobOffer(requestDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<JobOfferResponseDTO> updateJobOffer(@PathVariable Long id, @RequestBody JobOfferRequestDTO requestDTO) {
        return ResponseEntity.ok(jobOfferService.updateJobOffer(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<Void> deleteJobOffer(@PathVariable Long id) {
        jobOfferService.deleteJobOffer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER')")
    public ResponseEntity<List<JobOfferResponseDTO>> getAllJobOffers() {
        return ResponseEntity.ok(jobOfferService.getAllJobOffers());
    }

    @GetMapping("/published")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<JobOfferResponseDTO>> getPublishedJobOffers() {
        return ResponseEntity.ok(jobOfferService.getPublishedJobOffers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<JobOfferResponseDTO> getJobOfferById(@PathVariable Long id) {
        return ResponseEntity.ok(jobOfferService.getJobOfferById(id));
    }
}

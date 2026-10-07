package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.JobOfferResponseDTO;
import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.entity.JobOfferStatus;
import com.esprit.microservice.hrbackend.mapper.JobOfferMapper;
import com.esprit.microservice.hrbackend.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final JobOfferRepository jobOfferRepository;

    @GetMapping("/offers")
    public ResponseEntity<List<JobOfferResponseDTO>> getAllPublicOffers() {
        List<JobOfferResponseDTO> offers = jobOfferRepository.findByStatus(JobOfferStatus.PUBLISHED).stream()
                .map(JobOfferMapper::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(offers);
    }

    @GetMapping("/offers/{id}")
    public ResponseEntity<JobOfferResponseDTO> getPublicJobOffer(@PathVariable Long id) {
        JobOffer offer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer not found"));
        
        if (offer.getStatus() != JobOfferStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer is not published");
        }

        return ResponseEntity.ok(JobOfferMapper.toResponseDTO(offer));
    }
}

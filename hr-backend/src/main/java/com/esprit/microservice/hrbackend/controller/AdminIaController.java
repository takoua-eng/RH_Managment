package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.ia.AiRankingItem;
import com.esprit.microservice.hrbackend.entity.Candidate;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;
import com.esprit.microservice.hrbackend.repository.CandidateRepository;
import com.esprit.microservice.hrbackend.service.AiMatchingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ia")
@PreAuthorize("hasAnyRole('ADMIN', 'RH')")
public class AdminIaController {

    private final CandidateRepository candidateRepository;
    private final AiMatchingService aiMatchingService;
    private final ObjectMapper objectMapper;

    public AdminIaController(CandidateRepository candidateRepository,
                             AiMatchingService aiMatchingService,
                             ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.aiMatchingService = aiMatchingService;
        this.objectMapper = objectMapper;
    }

    /** Candidats d'une offre, du plus compatible au moins compatible. */
    @GetMapping("/job-offers/{jobOfferId}/ranking")
    public List<AiRankingItem> classement(@PathVariable Long jobOfferId) {
        return candidateRepository.findAiRanking(jobOfferId).stream()
                .map(this::versDto)
                .toList();
    }

    /** Relance l'analyse d'un candidat (bouton en cas d'erreur). */
    @PostMapping("/candidates/{id}/analyze")
    public ResponseEntity<Void> relancer(@PathVariable Long id) {
        aiMatchingService.relancer(id);
        return ResponseEntity.accepted().build();
    }

    /** Analyse tous les candidats d'une offre qui ne l'ont pas encore été. */
    @PostMapping("/job-offers/{jobOfferId}/analyze")
    public ResponseEntity<String> analyserTout(@PathVariable Long jobOfferId) {
        List<Candidate> aAnalyser = candidateRepository.findAiRanking(jobOfferId).stream()
                .filter(c -> c.getAiStatus() != StatutAnalyse.TERMINEE)
                .toList();
        aAnalyser.forEach(c -> aiMatchingService.relancer(c.getId()));
        return ResponseEntity.accepted().body(aAnalyser.size() + " analyse(s) lancée(s)");
    }

    private AiRankingItem versDto(Candidate c) {
        return new AiRankingItem(
                c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getStatus(), c.getAiStatus(), c.getAiScore(), c.getAiRecommendation(),
                lireJson(c.getAiAnalysis()), c.getAiError()
        );
    }

    private JsonNode lireJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
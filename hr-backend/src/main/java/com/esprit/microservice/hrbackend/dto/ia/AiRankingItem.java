package com.esprit.microservice.hrbackend.dto.ia;

import com.esprit.microservice.hrbackend.entity.CandidateStatus;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;
import com.fasterxml.jackson.databind.JsonNode;

public record AiRankingItem(
        Long id,
        String firstName,
        String lastName,
        String email,
        CandidateStatus status,
        StatutAnalyse aiStatus,
        Double aiScore,
        String aiRecommendation,
        JsonNode aiAnalysis,
        String aiError
) {}
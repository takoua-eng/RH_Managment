package com.esprit.microservice.hrbackend.event;

public record CandidateAiAnalysisErrorEvent(Long candidateId, String errorMessage) {
}

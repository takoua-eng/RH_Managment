package com.esprit.microservice.hrbackend.event;

public record CandidateDecisionMadeEvent(Long candidateId, String status, Long actorEmployeeId) {
}

package com.esprit.microservice.hrbackend.event;

public record CandidateStatusChangedByManagerEvent(Long candidateId, String newStatus, Long managerId) {
}

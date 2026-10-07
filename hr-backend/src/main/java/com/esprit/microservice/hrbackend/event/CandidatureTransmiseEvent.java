package com.esprit.microservice.hrbackend.event;

public record CandidatureTransmiseEvent(
        Long candidateId,
        Long managerId
) {}

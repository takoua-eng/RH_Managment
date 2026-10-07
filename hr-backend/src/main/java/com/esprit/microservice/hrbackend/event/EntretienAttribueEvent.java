package com.esprit.microservice.hrbackend.event;

public record EntretienAttribueEvent(
        Long interviewId,
        String scheduledByUsername
) {}

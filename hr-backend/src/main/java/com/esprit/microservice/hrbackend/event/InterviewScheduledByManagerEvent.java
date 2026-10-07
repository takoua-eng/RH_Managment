package com.esprit.microservice.hrbackend.event;

public record InterviewScheduledByManagerEvent(Long interviewId, Long managerId) {
}

package com.esprit.microservice.hrbackend.event;

public record InterviewUpdatedEvent(Long interviewId, Long actorEmployeeId) {
}

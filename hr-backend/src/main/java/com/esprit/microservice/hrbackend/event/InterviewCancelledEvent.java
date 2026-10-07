package com.esprit.microservice.hrbackend.event;

public record InterviewCancelledEvent(Long interviewId, Long actorEmployeeId, String reason) {
}

package com.esprit.microservice.hrbackend.event;

public record InterviewFeedbackSubmittedEvent(Long interviewId, Long managerId) {
}

package com.esprit.microservice.hrbackend.event;

public record TrainingCompletedEvent(Long trainingId, Long employeeId) {
}

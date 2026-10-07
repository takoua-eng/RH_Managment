package com.esprit.microservice.hrbackend.event;

public record TrainingAssignedEvent(Long trainingId, Long employeeId, Long actorEmployeeId) {
}

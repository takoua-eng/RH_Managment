package com.esprit.microservice.hrbackend.event;

public record LeaveStatusChangedEvent(Long leaveId, String newStatus, Long actorEmployeeId) {
}

package com.esprit.microservice.hrbackend.event;

public record LeaveRequestSubmittedEvent(
        Long leaveId,
        Long managerId
) {}

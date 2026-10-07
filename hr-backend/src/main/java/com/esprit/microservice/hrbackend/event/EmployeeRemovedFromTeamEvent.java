package com.esprit.microservice.hrbackend.event;

public record EmployeeRemovedFromTeamEvent(
        Long employeeId,
        Long oldManagerId,
        Long newManagerId
) {}

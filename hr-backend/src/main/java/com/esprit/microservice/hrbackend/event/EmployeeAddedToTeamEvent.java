package com.esprit.microservice.hrbackend.event;

public record EmployeeAddedToTeamEvent(
        Long employeeId,
        Long managerId
) {}

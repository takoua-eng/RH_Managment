package com.esprit.microservice.hrbackend.event;

public record EmployeeDepartmentChangedEvent(Long employeeId, String departmentName) {
}

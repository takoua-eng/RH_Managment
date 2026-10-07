package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.Status;
import java.time.LocalDate;

public record ManagerTeamMemberDTO(
    Long id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String position,
    Status status,
    LocalDate hireDate,
    String photo,
    String departmentName,
    boolean isOnLeaveToday
) {}

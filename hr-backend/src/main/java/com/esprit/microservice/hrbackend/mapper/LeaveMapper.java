package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.LeaveDTO;
import com.esprit.microservice.hrbackend.entity.Leave;

public class LeaveMapper {

    public static LeaveDTO toDTO(Leave leave) {
        if (leave == null) {
            return null;
        }

        return LeaveDTO.builder()
                .id(leave.getId())
                .employeeId(leave.getEmployeeId())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .type(leave.getType())
                .status(leave.getStatus())
                .reason(leave.getReason())
                .createdAt(leave.getCreatedAt())
                .build();
    }

    public static Leave toEntity(LeaveDTO dto) {
        if (dto == null) {
            return null;
        }

        return Leave.builder()
                .id(dto.getId())
                .employeeId(dto.getEmployeeId())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .type(dto.getType())
                .status(dto.getStatus())
                .reason(dto.getReason())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}

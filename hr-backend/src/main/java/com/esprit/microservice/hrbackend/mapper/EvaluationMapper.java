package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.EvaluationDTO;
import com.esprit.microservice.hrbackend.entity.Evaluation;

public class EvaluationMapper {

    public static Evaluation toEntity(EvaluationDTO dto) {
        if (dto == null) {
            return null;
        }

        return Evaluation.builder()
                .id(dto.getId())
                .employeeId(dto.getEmployeeId())
                .managerId(dto.getManagerId())
                .date(dto.getDate())
                .communication(dto.getCommunication())
                .leadership(dto.getLeadership())
                .technical(dto.getTechnical())
                .teamwork(dto.getTeamwork())
                .productivity(dto.getProductivity())
                .comments(dto.getComments())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public static EvaluationDTO toDTO(Evaluation entity) {
        if (entity == null) {
            return null;
        }

        return EvaluationDTO.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployeeId())
                .managerId(entity.getManagerId())
                .date(entity.getDate())
                .communication(entity.getCommunication())
                .leadership(entity.getLeadership())
                .technical(entity.getTechnical())
                .teamwork(entity.getTeamwork())
                .productivity(entity.getProductivity())
                .comments(entity.getComments())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

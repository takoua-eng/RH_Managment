package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.JobOfferRequestDTO;
import com.esprit.microservice.hrbackend.dto.JobOfferResponseDTO;
import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.entity.JobOfferStatus;
import java.time.LocalDate;

public class JobOfferMapper {

    public static JobOffer toEntity(JobOfferRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        return JobOffer.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .department(dto.getDepartment())
                .contractType(dto.getContractType())
                .experienceLevel(dto.getExperienceLevel())
                .requiredSkills(dto.getRequiredSkills())
                .location(dto.getLocation())
                .salaryRange(dto.getSalaryRange())
                .publicationDate(LocalDate.now())
                .applicationDeadline(dto.getApplicationDeadline())
                .numberOfPositions(dto.getNumberOfPositions() != null ? dto.getNumberOfPositions() : 1)
                .status(dto.getStatus() != null ? dto.getStatus() : JobOfferStatus.DRAFT)
                .build();
    }

    public static JobOfferResponseDTO toResponseDTO(JobOffer entity) {
        if (entity == null) {
            return null;
        }

        return JobOfferResponseDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .department(entity.getDepartment())
                .contractType(entity.getContractType())
                .experienceLevel(entity.getExperienceLevel())
                .requiredSkills(entity.getRequiredSkills())
                .location(entity.getLocation())
                .salaryRange(entity.getSalaryRange())
                .publicationDate(entity.getPublicationDate())
                .applicationDeadline(entity.getApplicationDeadline())
                .numberOfPositions(entity.getNumberOfPositions())
                .status(entity.getStatus())
                .build();
    }
}

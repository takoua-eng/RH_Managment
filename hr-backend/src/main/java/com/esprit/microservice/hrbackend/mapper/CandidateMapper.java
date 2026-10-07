package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.CandidateRequestDTO;
import com.esprit.microservice.hrbackend.dto.CandidateResponseDTO;
import com.esprit.microservice.hrbackend.entity.Candidate;

public class CandidateMapper {

    public static Candidate toEntity(CandidateRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        Candidate candidate = new Candidate();
        candidate.setFirstName(dto.getFirstName());
        candidate.setLastName(dto.getLastName());
        candidate.setEmail(dto.getEmail());
        candidate.setPhone(dto.getPhone());
        candidate.setAddress(dto.getAddress());
        candidate.setEducation(dto.getEducation());
        candidate.setExperience(dto.getExperience());
        candidate.setSkills(dto.getSkills());

        return candidate;
    }

    public static CandidateResponseDTO toResponseDTO(Candidate entity) {
        if (entity == null) {
            return null;
        }

        CandidateResponseDTO dto = new CandidateResponseDTO();
        dto.setId(entity.getId());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setAddress(entity.getAddress());
        dto.setEducation(entity.getEducation());
        dto.setExperience(entity.getExperience());
        dto.setSkills(entity.getSkills());
        dto.setApplicationDate(entity.getApplicationDate());
        dto.setTransmissionDate(entity.getTransmissionDate());
        dto.setDecisionDate(entity.getDecisionDate());
        dto.setStatus(entity.getStatus());

        if (entity.getJobOffer() != null) {
            dto.setJobOfferId(entity.getJobOffer().getId());
            dto.setJobOfferTitle(entity.getJobOffer().getTitle());
            dto.setJobOfferDepartment(entity.getJobOffer().getDepartment());
        }

        if (entity.getAssignedManager() != null) {
            dto.setAssignedManagerId(entity.getAssignedManager().getId());
            dto.setAssignedManagerName(entity.getAssignedManager().getFirstName() + " " + entity.getAssignedManager().getLastName());
        }

        dto.setHasCv(entity.getCvPath() != null && !entity.getCvPath().isEmpty());
        dto.setCvFileName(entity.getCvFileName());

        dto.setHasMotivationLetter(entity.getMotivationLetterPath() != null && !entity.getMotivationLetterPath().isEmpty());
        dto.setMotivationLetterFileName(entity.getMotivationLetterFileName());

        dto.setAiScore(entity.getAiScore());
        dto.setAiRecommendation(entity.getAiRecommendation());
        dto.setAiStatus(entity.getAiStatus());
        dto.setAiError(entity.getAiError());
        dto.setAiAnalysis(entity.getAiAnalysis());

        return dto;
    }
}

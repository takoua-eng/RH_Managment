package com.esprit.microservice.hrbackend.dto;

import com.esprit.microservice.hrbackend.entity.CandidateStatus;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CandidateResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String education;
    private String experience;
    private String skills;
    private LocalDate applicationDate;
    private LocalDate transmissionDate;
    private LocalDate decisionDate;
    private CandidateStatus status;
    private Long jobOfferId;
    private String jobOfferTitle;
    private String jobOfferDepartment;
    
    private Long assignedManagerId;
    private String assignedManagerName;
    
    // File metadata (not the byte array to save bandwidth)
    private boolean hasCv;
    private String cvFileName;
    
    private boolean hasMotivationLetter;
    private String motivationLetterFileName;

    // AI Analysis fields
    private Double aiScore;
    private String aiRecommendation;
    private StatutAnalyse aiStatus;
    private String aiError;
    @JsonRawValue
    private String aiAnalysis;
}

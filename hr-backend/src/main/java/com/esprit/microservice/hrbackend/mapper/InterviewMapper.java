package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.InterviewResponseDTO;
import com.esprit.microservice.hrbackend.entity.Interview;

public class InterviewMapper {

    public static InterviewResponseDTO toResponseDTO(Interview entity) {
        if (entity == null) return null;

        InterviewResponseDTO.InterviewResponseDTOBuilder builder = InterviewResponseDTO.builder()
                .id(entity.getId())
                .interviewDate(entity.getInterviewDate())
                .interviewTime(entity.getInterviewTime())
                .durationMinutes(entity.getDurationMinutes())
                .type(entity.getType())
                .locationOrLink(entity.getLocationOrLink())
                .comment(entity.getComment())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .modificationCount(entity.getModificationCount())
                .emailSent(entity.getEmailSent())
                .emailSentAt(entity.getEmailSentAt())
                .cancelledAt(entity.getCancelledAt())
                .cancellationReason(entity.getCancellationReason())
                // ===== Questions IA =====
                .aiQuestions(entity.getAiQuestions())
                .aiQuestionsStatus(entity.getAiQuestionsStatus())
                .aiQuestionsError(entity.getAiQuestionsError())
                // ===== Notes et Avis du Manager =====
                .interviewNotes(entity.getInterviewNotes())
                .managerRecommendation(entity.getManagerRecommendation())
                .managerRating(entity.getManagerRating())
                .managerFeedback(entity.getManagerFeedback())
                .feedbackSubmittedAt(entity.getFeedbackSubmittedAt());

        if (entity.getCandidate() != null) {
            builder.candidateId(entity.getCandidate().getId())
                    .candidateFirstName(entity.getCandidate().getFirstName())
                    .candidateLastName(entity.getCandidate().getLastName())
                    .candidateEmail(entity.getCandidate().getEmail());
        }

        if (entity.getJobOffer() != null) {
            builder.jobOfferId(entity.getJobOffer().getId())
                    .jobOfferTitle(entity.getJobOffer().getTitle())
                    .department(entity.getJobOffer().getDepartment());
        }

        if (entity.getManager() != null) {
            builder.managerId(entity.getManager().getId())
                    .managerName(entity.getManager().getFirstName() + " " + entity.getManager().getLastName());
        }

        return builder.build();
    }
}
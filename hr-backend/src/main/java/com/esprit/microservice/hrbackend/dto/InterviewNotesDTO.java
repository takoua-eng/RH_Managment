package com.esprit.microservice.hrbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewNotesDTO {

    private List<QuestionNoteDTO> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionNoteDTO {
        private Integer index;
        private Boolean posee;
        private String notes;
    }
}

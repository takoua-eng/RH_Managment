package com.esprit.microservice.hrbackend.dto.ia;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuestionsRequest(
        @JsonProperty("candidature_id") Long candidatureId,
        @JsonProperty("cv_path") String cvPath,
        @JsonProperty("cv_text") String cvText,
        OffreIa offre
) {}
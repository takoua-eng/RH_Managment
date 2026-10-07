package com.esprit.microservice.hrbackend.dto.ia;


import com.fasterxml.jackson.annotation.JsonProperty;

public record MatchRequest(
        @JsonProperty("candidature_id") Long candidatureId,
        @JsonProperty("cv_path") String cvPath,
        @JsonProperty("lettre_path") String lettrePath,
        OffreIa offre
) {}

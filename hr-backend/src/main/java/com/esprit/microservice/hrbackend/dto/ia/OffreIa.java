package com.esprit.microservice.hrbackend.dto.ia;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OffreIa(
        String titre,
        String description,
        @JsonProperty("type_offre") String typeOffre,
        @JsonProperty("competences_requises") List<String> competencesRequises,
        @JsonProperty("experience_min") double experienceMin,
        @JsonProperty("niveau_etudes_min") int niveauEtudesMin,
        @JsonProperty("langues_requises") List<String> languesRequises
) {}
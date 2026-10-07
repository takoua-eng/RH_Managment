package com.esprit.microservice.hrbackend.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDTO {
    private Long id;
    private String name;
    private String type; // "Contrat", "Fiche de paie", "Diplôme", "Attestation", etc.
    private String contentType;
    private Long size;
    private LocalDate uploadDate;
    private Long employeeId;
    private String employeeName;
}

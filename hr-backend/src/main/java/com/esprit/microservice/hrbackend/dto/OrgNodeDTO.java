package com.esprit.microservice.hrbackend.dto;



import lombok.*;
import java.util.List;

// Structure récursive pour l'organigramme
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrgNodeDTO {
    private Long id;
    private String name;
    private String position;
    private String department;
    private String photoUrl;
    private List<OrgNodeDTO> children;
}

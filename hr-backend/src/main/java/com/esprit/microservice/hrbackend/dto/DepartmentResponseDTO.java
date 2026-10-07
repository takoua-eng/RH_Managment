package com.esprit.microservice.hrbackend.dto;



import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DepartmentResponseDTO {
    private Long id;
    private String name;
    private String description;
    private EmployeeSummaryDTO manager; // résumé léger de l'employé
    private int employeeCount;
    private String location;
    private Double budget;
}

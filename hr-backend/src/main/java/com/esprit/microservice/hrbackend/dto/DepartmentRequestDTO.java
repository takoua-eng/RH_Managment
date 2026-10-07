package com.esprit.microservice.hrbackend.dto;



import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DepartmentRequestDTO {
    private String name;
    private String description;
    private Long managerId; // id de l'employé désigné chef de département
    private String location;
    private Double budget;
}

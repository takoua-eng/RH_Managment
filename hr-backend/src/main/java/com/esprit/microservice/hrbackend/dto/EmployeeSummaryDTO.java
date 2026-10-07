package com.esprit.microservice.hrbackend.dto;



import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeSummaryDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String position;
    private String photoUrl;
}
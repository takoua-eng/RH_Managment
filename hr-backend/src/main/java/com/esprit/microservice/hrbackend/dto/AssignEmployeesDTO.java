package com.esprit.microservice.hrbackend.dto;



import lombok.*;
import java.util.List;

// Pour l'affectation d'employés à un département
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssignEmployeesDTO {
    private List<Long> employeeIds;
}

package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    private String position;

    private String department;

    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String keycloakId;

    private Double salary;

    @Column(columnDefinition = "BYTEA")
    private byte[] photo;

    private String photoContentType;
}

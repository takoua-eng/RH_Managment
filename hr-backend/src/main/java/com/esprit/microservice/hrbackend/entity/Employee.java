package com.esprit.microservice.hrbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

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

    //private String department;

    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String keycloakId;

    private Double salary;

    @Builder.Default
    private Integer availableLeaveDays = 25;

    private String address;

    @Column(name = "photo_path")
    private String photoPath;

    private String photoContentType;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    // Auto-référence pour la hiérarchie manager/employé (utile pour l'organigramme)
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @OneToMany(mappedBy = "manager")
    @Builder.Default
    private Set<Employee> subordinates = new HashSet<>();
}

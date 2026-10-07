package com.esprit.microservice.hrbackend.dto;

import lombok.Data;

@Data
public class CandidateRequestDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String education;
    private String experience;
    private String skills;
    private Long jobOfferId;
}

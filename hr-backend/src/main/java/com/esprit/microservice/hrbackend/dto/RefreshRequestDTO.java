package com.esprit.microservice.hrbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefreshRequestDTO {
    @JsonProperty("refresh_token")
    private String refreshToken;

    public RefreshRequestDTO() {
    }

    public RefreshRequestDTO(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

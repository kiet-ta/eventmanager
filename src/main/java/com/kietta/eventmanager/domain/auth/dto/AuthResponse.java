package com.kietta.eventmanager.domain.auth.dto;

import lombok.Getter;

@Getter
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;

    public AuthResponse(String accessToken, String tokenType) {
        this(accessToken, null, tokenType);
    }

    public AuthResponse(String accessToken, String refreshToken, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
    }
}

package com.kietta.eventmanager.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}

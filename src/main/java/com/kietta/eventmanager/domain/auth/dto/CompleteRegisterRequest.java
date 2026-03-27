package com.kietta.eventmanager.domain.auth.dto;

import com.kietta.eventmanager.core.constant.IdentityUserType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteRegisterRequest {
    @NotBlank(message = "Register token is required")
    private String registerToken;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Identity number is required")
    private String identityNumber;

    private IdentityUserType identityType = IdentityUserType.CCCD;
}


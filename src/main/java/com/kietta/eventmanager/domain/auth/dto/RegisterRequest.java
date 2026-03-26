package com.kietta.eventmanager.domain.auth.dto;

import com.kietta.eventmanager.core.constant.IdentityUserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @Email(message = "Email should be valid")
    private String email;
    private String otp;

    @NotBlank(message = "Password cannot be blank")
    @NotEmpty(message = "Password cannot be empty")
    private String password;

    private String firstName;
    private String lastName;

    @NotBlank(message = "Identity number cannot be blank")
    private String identityNumber;
    private IdentityUserType identityType = IdentityUserType.CCCD;
}

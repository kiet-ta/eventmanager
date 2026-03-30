package com.kietta.eventmanager.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VerifyOtpResponse {
    private String status;
    private String message;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String registerToken;

    public static VerifyOtpResponse loginSuccess(String accessToken, String refreshToken) {
        return new VerifyOtpResponse(
                "LOGIN_SUCCESS",
                "OTP hop le, dang nhap thanh cong",
                accessToken,
                refreshToken,
                "Bearer",
                null
        );
    }

    public static VerifyOtpResponse registrationRequired(String registerToken) {
        return new VerifyOtpResponse(
                "REGISTRATION_REQUIRED",
                "Chúc mừng bạn nha, còn 1 bước cuối cùng, tiếp tục nào !!!",
                null,
                null,
                null,
                registerToken
        );
    }
}


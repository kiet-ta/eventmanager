package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.core.constant.IdentityUserType;
import com.kietta.eventmanager.domain.auth.dto.RegisterRequest;
import com.kietta.eventmanager.domain.auth.service.AuthService;
import com.kietta.eventmanager.domain.auth.service.NotificationService;
import com.kietta.eventmanager.domain.auth.service.OtpService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

class AuthControllerTest {

    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final OtpService otpService = Mockito.mock(OtpService.class);
    private final AuthService authService = Mockito.mock(AuthService.class);
    private final AuthController authController = new AuthController(notificationService, otpService, authService, null);

    @Test
    void registerUser_success_returnsOk() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");
        request.setPassword("P@ssw0rd123");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setIdentityNumber("079123456789");
        request.setIdentityType(IdentityUserType.CCCD);

        doNothing().when(authService).register(any(RegisterRequest.class));

        var response = authController.registerUser(request);

        assertEquals(OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void registerUser_whenServiceThrows_returnsBadRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");
        request.setPassword("P@ssw0rd123");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setIdentityNumber("079123456789");
        request.setIdentityType(IdentityUserType.CCCD);

        doThrow(new IllegalArgumentException("Mã OTP không chính xác!"))
                .when(authService).register(any(RegisterRequest.class));

        var response = authController.registerUser(request);

        assertEquals(BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
    }

    @Test
    void sendOtp_missingEmail_returnsBadRequest() {
        var response = authController.sendOtp(Map.of());

        assertEquals(BAD_REQUEST, response.getStatusCode());

        verifyNoInteractions(otpService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendOtp_success_returnsOkAndSendsMail() {
        Mockito.when(otpService.generateAndSaveOtp("user@example.com"))
                .thenReturn("654321");

        var response = authController.sendOtp(Map.of("email", "user@example.com"));

        assertEquals(OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        verify(otpService).generateAndSaveOtp("user@example.com");
        verify(notificationService).sendHelloWorld(eq("user@example.com"), eq("654321"));
    }
}




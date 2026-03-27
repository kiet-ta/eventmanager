package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.core.constant.IdentityUserType;
import com.kietta.eventmanager.domain.auth.dto.AuthResponse;
import com.kietta.eventmanager.domain.auth.dto.CompleteRegisterRequest;
import com.kietta.eventmanager.domain.auth.dto.SendOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpResponse;
import com.kietta.eventmanager.domain.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

class AuthControllerTest {

    private final AuthService authService = Mockito.mock(AuthService.class);
    private final AuthController authController = new AuthController(authService);

    @Test
    void sendOtp_success_returnsOk() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("user@example.com");
        request.setRecaptchaToken("token");

        doNothing().when(authService).sendOtp(any(SendOtpRequest.class));

        var response = authController.sendOtp(request);

        assertEquals(OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
        verify(authService).sendOtp(any(SendOtpRequest.class));
    }

    @Test
    void sendOtp_whenServiceThrows_returnsBadRequest() {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("user@example.com");
        request.setRecaptchaToken("token");

        doThrow(new IllegalArgumentException("Xac thuc reCAPTCHA that bai"))
                .when(authService).sendOtp(any(SendOtpRequest.class));

        var response = authController.sendOtp(request);

        assertEquals(BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
    }

    @Test
    void verifyOtp_whenExistingUser_returnsOk() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");

        Mockito.when(authService.verifyOtp(any(VerifyOtpRequest.class)))
                .thenReturn(VerifyOtpResponse.loginSuccess("jwt-token"));

        var response = authController.verifyOtp(request);

        assertEquals(OK, response.getStatusCode());
        assertInstanceOf(VerifyOtpResponse.class, response.getBody());
    }

    @Test
    void verifyOtp_whenNewUser_returnsAccepted() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("new@example.com");
        request.setOtp("123456");

        Mockito.when(authService.verifyOtp(any(VerifyOtpRequest.class)))
                .thenReturn(VerifyOtpResponse.registrationRequired("register-token"));

        var response = authController.verifyOtp(request);

        assertEquals(ACCEPTED, response.getStatusCode());
        assertInstanceOf(VerifyOtpResponse.class, response.getBody());
    }

    @Test
    void verifyOtp_whenLocked_returns429() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("new@example.com");
        request.setOtp("123456");

        doThrow(new ResponseStatusException(TOO_MANY_REQUESTS, "locked"))
                .when(authService).verifyOtp(any(VerifyOtpRequest.class));

        var response = authController.verifyOtp(request);

        assertEquals(TOO_MANY_REQUESTS, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
    }

    @Test
    void completeRegister_success_returnsOk() {
        CompleteRegisterRequest request = new CompleteRegisterRequest();
        request.setRegisterToken("register-token");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setIdentityNumber("079123456789");
        request.setIdentityType(IdentityUserType.CCCD);

        Mockito.when(authService.completeRegister(any(CompleteRegisterRequest.class)))
                .thenReturn(new AuthResponse("jwt", "Bearer"));

        var response = authController.completeRegister(request);

        assertEquals(OK, response.getStatusCode());
        assertInstanceOf(AuthResponse.class, response.getBody());
    }

    @Test
    void completeRegister_whenInvalid_returnsBadRequest() {
        CompleteRegisterRequest request = new CompleteRegisterRequest();
        request.setRegisterToken("invalid-token");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setIdentityNumber("079123456789");

        doThrow(new IllegalArgumentException("Register token khong hop le"))
                .when(authService).completeRegister(any(CompleteRegisterRequest.class));

        var response = authController.completeRegister(request);

        assertEquals(BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());
    }
}




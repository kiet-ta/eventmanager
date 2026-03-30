package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.core.security.CookieUtils;
import com.kietta.eventmanager.domain.auth.dto.AuthResponse;
import com.kietta.eventmanager.domain.auth.dto.CompleteRegisterRequest;
import com.kietta.eventmanager.domain.auth.dto.SendOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpResponse;
import com.kietta.eventmanager.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CookieUtils cookieUtils;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        try  {
            authService.sendOtp(request);
            return ResponseEntity.ok(Map.of("message", "Ma OTP da duoc gui"));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping({"/verify-otp", "/login"})
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletResponse servletResponse) {
        try {
            VerifyOtpResponse response = authService.verifyOtp(request);
            if ("REGISTRATION_REQUIRED".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }

            cookieUtils.setRefreshTokenCookie(servletResponse, response.getRefreshToken());
            VerifyOtpResponse sanitized = new VerifyOtpResponse(
                    response.getStatus(),
                    response.getMessage(),
                    response.getAccessToken(),
                    null,
                    response.getTokenType(),
                    null
            );
            return ResponseEntity.ok(sanitized);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/complete-register")
    public ResponseEntity<?> completeRegister(@Valid @RequestBody CompleteRegisterRequest request, HttpServletResponse servletResponse) {
        try {
            AuthResponse response = authService.completeRegister(request);
            cookieUtils.setRefreshTokenCookie(servletResponse, response.getRefreshToken());
            return ResponseEntity.ok(new AuthResponse(response.getAccessToken(), response.getTokenType()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse servletResponse
    ) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Khong tim thay refresh token cookie"));
            }

            AuthResponse response = authService.refreshAccessToken(refreshToken);
            cookieUtils.setRefreshTokenCookie(servletResponse, response.getRefreshToken());
            return ResponseEntity.ok(new AuthResponse(response.getAccessToken(), response.getTokenType()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                                    HttpServletResponse servletResponse) {
        try {
            if (refreshToken != null && !refreshToken.isBlank()) {
                authService.logout(refreshToken);
            }
            cookieUtils.clearRefreshTokenCookie(servletResponse);
            return ResponseEntity.ok(Map.of("message", "Dang xuat thanh cong"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}

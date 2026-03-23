package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.domain.auth.service.AuthService;
import com.kietta.eventmanager.domain.auth.service.NotificationService;
import com.kietta.eventmanager.domain.auth.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final NotificationService notificationService;
    private final OtpService otpService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
        try  {
            authService.registerWithOtp(payload);
            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công! Welcome to the system."));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        // 1. CREATE OTP AND SAVE IN Redis
        String generatedOtp = otpService.generateAndSaveOtp(email);
        notificationService.sendHelloWorld(email, generatedOtp);

        return ResponseEntity.ok(Map.of("message", "Mã xác thực đã được gửi đến " + email));
    }

}

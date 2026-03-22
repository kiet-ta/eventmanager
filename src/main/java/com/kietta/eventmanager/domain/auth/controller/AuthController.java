package com.kietta.eventmanager.domain.auth.controller;

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

package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.domain.auth.dto.AuthResponse;
import com.kietta.eventmanager.domain.auth.dto.LoginRequest;
import com.kietta.eventmanager.domain.auth.dto.RegisterRequest;
import com.kietta.eventmanager.domain.auth.service.AuthService;
import com.kietta.eventmanager.domain.auth.service.JwtService;
import com.kietta.eventmanager.domain.auth.service.NotificationService;
import com.kietta.eventmanager.domain.auth.service.OtpService;
import com.kietta.eventmanager.domain.user_identities.entity.UserIdentity;
import com.kietta.eventmanager.domain.user_identities.repository.UserIdentityRepository;
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
    private final UserIdentityRepository userIdentityRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // If the password is incorrect or the email is not found, return a 401 Unauthorized message.
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // FLOW REGISTER
    // User fill form -> Click "Send OTP"
    // -> System generate OTP and save in Redis
    // -> System send OTP to user email
    // -> User receive OTP and fill in form
    // -> Click "Register"
    // -> System check OTP in Redis
    /** -> IF OTP IS VALID, CREATE USER ACCOUND, CREATE JWT AND MUST DELETE OTP IN REDIS**/
    // -> Return success message
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
        try  {
            authService.register(request);
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

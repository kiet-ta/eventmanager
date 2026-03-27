package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.domain.auth.dto.AuthResponse;
import com.kietta.eventmanager.domain.auth.dto.CompleteRegisterRequest;
import com.kietta.eventmanager.domain.auth.dto.SendOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpRequest;
import com.kietta.eventmanager.domain.auth.dto.VerifyOtpResponse;
import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.user.repository.UserRepository;
import com.kietta.eventmanager.domain.user_identities.entity.UserIdentity;
import com.kietta.eventmanager.domain.user_identities.repository.UserIdentityRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private static final String LOCAL_PROVIDER = "LOCAL";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RecaptchaService recaptchaService;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final JwtService jwtService;

    public void sendOtp(SendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        recaptchaService.verifyToken(request.getRecaptchaToken());

        String generatedOtp = otpService.generateAndSaveOtp(email);
        notificationService.sendOtpCode(email, generatedOtp);
        log.info("OTP was sent to {}", email);
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());

        otpService.assertNotLocked(email);
        otpService.verifyOtpOrThrow(email, request.getOtp());

        return userIdentityRepository
                .findByProviderIgnoreCaseAndProviderIdIgnoreCase(LOCAL_PROVIDER, email)
                .map(identity -> {
                    String accessToken = jwtService.generateToken(identity.getUser().getId(), email);
                    return VerifyOtpResponse.loginSuccess(accessToken);
                })
                .orElseGet(() -> {
                    String registerToken = jwtService.generateRegisterToken(email);
                    return VerifyOtpResponse.registrationRequired(registerToken);
                });
    }

    @Transactional
    public AuthResponse completeRegister(CompleteRegisterRequest request) {
        String email = normalizeEmail(jwtService.extractRegisterEmail(request.getRegisterToken()));

        if (userIdentityRepository.existsByProviderIgnoreCaseAndProviderIdIgnoreCase(LOCAL_PROVIDER, email)) {
            throw new IllegalArgumentException("Email nay da duoc su dung");
        }

        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setIdentityNumber(request.getIdentityNumber());
        newUser.setIdentityType(request.getIdentityType());
        userRepository.save(newUser);

        UserIdentity identity = new UserIdentity();
        identity.setUser(newUser);
        identity.setProvider(LOCAL_PROVIDER);
        identity.setProviderId(email);
        identity.setVerified(true);
        userIdentityRepository.save(identity);

        String accessToken = jwtService.generateToken(newUser.getId(), email);
        log.info("Completed passwordless registration for {}", email);
        return new AuthResponse(accessToken, "Bearer");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

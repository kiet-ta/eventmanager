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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

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
    private final RefreshTokenService refreshTokenService;

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

        VerifyOtpResponse response = userIdentityRepository
                .findByProviderIgnoreCaseAndProviderIdIgnoreCase(LOCAL_PROVIDER, email)
                .map(identity -> {
                    AuthResponse authTokens = issueAuthTokens(identity.getUser(), email);
                    return VerifyOtpResponse.loginSuccess(authTokens.getAccessToken(), authTokens.getRefreshToken());
                })
                .orElseGet(() -> {
                    String registerToken = jwtService.generateRegisterToken(email);
                    return VerifyOtpResponse.registrationRequired(registerToken);
                });

        otpService.clearOtpState(email);
        return response;
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

        AuthResponse authTokens = issueAuthTokens(newUser, email);
        log.info("Completed passwordless registration for {}", email);
        return authTokens;
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(refreshToken);

        // Kiểm tra JTI trong family có match không
        if (!refreshTokenService.isValidJtiForFamily(payload.familyId(), payload.jti())) {
            // ⚠️ Token theft detected! Revoke toàn bộ family
            refreshTokenService.revokeFamily(payload.familyId());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token khong hop le hoac da bi su dung. Tai khoan da bi khoa."
            );
        }

        // ✅ Valid! Perform rotation: issue new tokens, update JTI in family
        String email = normalizeEmail(payload.email());

        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nguoi dung khong ton tai"));

        String accessToken = jwtService.generateAccessToken(user, email);
        String newRefreshToken = jwtService.generateRefreshToken(payload.userId(), email, payload.familyId());

        JwtService.RefreshTokenPayload newPayload = jwtService.extractRefreshTokenPayload(newRefreshToken);
        refreshTokenService.saveRefreshTokenToFamily(payload.familyId(), newPayload.jti(), jwtService.getRefreshTokenExpiration());

        return new AuthResponse(accessToken, newRefreshToken, "Bearer");
    }

    public void logout(String refreshToken) {
        try {
            JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(refreshToken);
            refreshTokenService.revokeFamily(payload.familyId());
        } catch (ResponseStatusException ignored) {
            // Idempotent logout: keep same response even when token is malformed/expired.
        }
    }

    private AuthResponse issueAuthTokens(User user, String email) {
        String accessToken = jwtService.generateAccessToken(user, email);
        String familyId = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(user.getId(), email, familyId);

        JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(refreshToken);
        refreshTokenService.saveRefreshTokenToFamily(familyId, payload.jti(), jwtService.getRefreshTokenExpiration());

        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

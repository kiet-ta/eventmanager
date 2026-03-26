package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.core.constant.CacheConstants;
import com.kietta.eventmanager.domain.auth.dto.AuthResponse;
import com.kietta.eventmanager.domain.auth.dto.LoginRequest;
import com.kietta.eventmanager.domain.auth.dto.RegisterRequest;
import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.user.repository.UserRepository;
import com.kietta.eventmanager.domain.user_identities.entity.UserIdentity;
import com.kietta.eventmanager.domain.user_identities.repository.UserIdentityRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecaptchaService recaptchaService;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final JwtService jwtService;

    // FLOW LOGIN
    // CHECK EMAIL AND PASSWORD IN DB
    // IF CORRECT -> CREATE JWT
    public AuthResponse login(LoginRequest request)
    {
        UserIdentity identity = userIdentityRepository.findByProviderAndProviderId("local", request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), identity.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String jwtToken = jwtService.generateToken(
                identity.getUser().getId(),
                identity.getProviderId()
        );

        return new AuthResponse(jwtToken, "Bearer");
    }


    @PostMapping("/send-otp")
    public ResponseEntity<?> sentOtp(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String recaptcha = payload.get("recaptcha");

        if (email == null || recaptcha == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing data"));
        }

        try {
            recaptchaService.verifyToken(recaptcha);
            String generatedOtp = otpService.generateAndSaveOtp(email);

            notificationService.sendHelloWorld(email, generatedOtp);
            return  ResponseEntity.ok(Map.of("message","Mã xác thực đã được gửi đến " + email));
        } catch (IllegalArgumentException e) {
            // CATCH BOT
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // SET TRANSACTION IF THE RES IS ERROR INTERMEDIATE ROLL BACK
    @Transactional
    public void register(RegisterRequest request) {
        //CHECK OTP IN REDIS ARE MATCHING WITH THE inputOtp
        String email = request.getEmail();
        String inputOtp = request.getOtp();
        String redisKey = CacheConstants.OTP_PREFIX + email;

        String saveOtp = redisTemplate.opsForValue().get(redisKey);

        if (saveOtp == null) {
            throw new IllegalArgumentException("Mã OTP đã hết hạn hoặc không tồn tại!");
        }
        if (!saveOtp.equals(inputOtp)) {
            throw new IllegalArgumentException("Mã OTP không chính xác!");
        }

        // CHECK DUPLICATE SPAM MAIL
        if (userIdentityRepository.existsByProviderAndProviderId("LOCAL", email)) {
            throw new IllegalArgumentException("Email này đã được sử dụng!");
        }

        // IF ALL OF ABOVE ARE SUCCESS, INTERMEDIATE DELETE THE OTP
        redisTemplate.delete(redisKey);

        //HASH RAWPASSWORD AND SAVE TO DB
        //SAVE DATA USER
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setIdentityNumber(request.getIdentityNumber());
        newUser.setIdentityType(request.getIdentityType());

        userRepository.save(newUser);

        //SAVE DATA AUTH
        UserIdentity identity = new UserIdentity();
        identity.setUser(newUser);
        identity.setProvider("LOCAL");
        identity.setProviderId(email);
        identity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        identity.setVerified(true); // PASS OTP VERIFY IN EMAIL, SO SET VERIFIED TRUE
        userIdentityRepository.save(identity);

        log.info("Đã đăng ký thành công user: {}", email);
    }
}

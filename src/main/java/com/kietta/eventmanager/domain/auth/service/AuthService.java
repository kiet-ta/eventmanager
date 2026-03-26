package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.core.constant.CacheConstants;
import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecaptchaService recaptchaService;
    private final OtpService otpService;
    private final NotificationService notificationService;

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
    public void registerWithOtp(Map<String,String> payload) {
        String email = payload.get("email");
        String inputOtp = payload.get("otp");
        String rawPassword = payload.get("password");
        String firstname = payload.get("firstname");
        String lastname = payload.get("lastname");

        //CHECK OTP IN REDIS ARE MATCHING WITH THE inputOtp
        String redisKey = CacheConstants.OTP_PREFIX + email;
        String saveOtp = redisTemplate.opsForValue().get(redisKey);

        if (saveOtp == null) {
            throw new IllegalArgumentException("Mã OTP đã hết hạn hoặc không tồn tại!");
        }
        if (!saveOtp.equals(inputOtp)) {
            throw new IllegalArgumentException("Mã OTP không chính xác!");
        }

        // CHECK DUPLICATE SPAM MAIL
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email này đã được sử dụng!");
        }

        // IF ALL OF ABOVE ARE SUCCESS, INTERMEDIATE DELETE THE OTP
        redisTemplate.delete(redisKey);

        //HASH RAWPASSWORD AND SAVE TO DB
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstname);
        user.setLastName(lastname);

        userRepository.save(user);
    }
}

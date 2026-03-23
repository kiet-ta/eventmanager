package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.core.constant.CacheConstants;
import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // SET TRANSACTION IF THE RES IS ERROR INTERMEDIATE ROLL BACK
    @Transactional
    public void registerWithOtp(Map<String,String> payload) {
        String email = payload.get("email");
        String inputOtp = payload.get("otp");
        String rawPassword = payload.get("password"); // Bạn gửi 'password'
        String fullName = payload.get("fullName");     // Bạn gửi 'fullName'

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

        // Tách fullName thành firstName và lastName
        if (fullName != null && !fullName.isEmpty()) {
            String[] parts = fullName.trim().split("\\s+", 2);
            user.setFirstName(parts[0]);
            user.setLastName(parts.length > 1 ? parts[1] : "");
        } else {
            user.setFirstName("Unknown");
            user.setLastName("");
        }

        userRepository.save(user);
    }
}

package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.core.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;


@Slf4j
@Service
@RequiredArgsConstructor // this check if the final field is not null and generate a constructor for it
public class OtpService {
    // Problem: how to handle send Opt to 10000 users?
    // Solution: use tool to store the OTP and set an expiration time for each OTP about 5 minutes.
    // Redis is a best practice for this because it is an in-memory data store that provides fast read and write operations
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // This is a wrapper connection with Redis by Spring Boot
    public final StringRedisTemplate redisTemplate;

    @Value("${app.security.otp.validity-minutes:5}")
    private long otpValidityMinutes;

    @Value("${app.security.otp.max-failed-attempts:5}")
    private int maxFailedAttempts;

    public String generateAndSaveOtp(String email) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpKey = otpKey(email);
        String failKey = failKey(email);

        redisTemplate.opsForValue().set(otpKey, otp, otpValidityMinutes, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(failKey, "0", otpValidityMinutes, TimeUnit.MINUTES);

        log.info("Generated OTP for email={} with ttl={}m", email, otpValidityMinutes);
        return otp;
    }

    public void assertNotLocked(String email) {
        int fails = getFailedAttempts(email);
        if (fails >= maxFailedAttempts) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Tai khoan bi khoa tam thoi, vui long thu lai sau 5 phut"
            );
        }
    }

    public void verifyOtpOrThrow(String email, String inputOtp) {
        String savedOtp = redisTemplate.opsForValue().get(otpKey(email));
        if (savedOtp == null) {
            throw new IllegalArgumentException("OTP da het han hoac khong ton tai");
        }

        if (!savedOtp.equals(inputOtp)) {
            int fails = increaseFailedAttempts(email);
            if (fails >= maxFailedAttempts) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Tai khoan bi khoa tam thoi, vui long thu lai sau 5 phut"
                );
            }
            throw new IllegalArgumentException("OTP khong dung");
        }
    }

    public void clearOtpState(String email) {
        redisTemplate.delete(otpKey(email));
        redisTemplate.delete(failKey(email));
    }

    private int getFailedAttempts(String email) {
        String rawValue = redisTemplate.opsForValue().get(failKey(email));
        if (rawValue == null) {
            return 0;
        }

        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int increaseFailedAttempts(String email) {
        String key = failKey(email);
        Long fails = redisTemplate.opsForValue().increment(key);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds < 0) {
            redisTemplate.expire(key, otpValidityMinutes, TimeUnit.MINUTES);
        }
        return fails == null ? 1 : fails.intValue();
    }

    private String otpKey(String email) {
        return CacheConstants.OTP_PREFIX + email;
    }

    private String failKey(String email) {
        return CacheConstants.OTP_FAILS_PREFIX + email;
    }
}

package com.kietta.eventmanager.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import com.kietta.eventmanager.core.constant.CacheConstants;


@Slf4j
@Service
@RequiredArgsConstructor // this check if the final field is not null and generate a constructor for it
public class OtpService {
    // Problem: how to handle send Opt to 10000 users?
    // Solution: use tool to store the OTP and set an expiration time for each OTP about 5 minutes.
    // Redis is a best practice for this because it is an in-memory data store that provides fast read and write operations

    // This is a wrapper connection with Redis by Spring Boot
    public final StringRedisTemplate redisTemplate;

    // SET TIME EXPIRATION FOR OTP, IT HELP IMPROVE SECURITY, USING BEAN
    @Value("${app.security.otp.validity-minutes:5}")
    private long OTP_VALIDITY_MINUTES;

    public String generateAndSaveOtp (String email) {
        // CREATE A OTP NUMBER WITH 6 DIGITS IMPROVE SECURITY, USING 'SECURENORMAL' IS BETTER THAN RANDOM
        SecureRandom random = new SecureRandom();
        int otpNum = 100000 + random.nextInt(900000); // GENERATE A 6 DIGIT OTP
        String otp = String.valueOf(otpNum);

        // CREATE KEY
        String redisKey = CacheConstants.OTP_PREFIX + email;

        // SAVE OTP TO REDIS WITH EXPIRATION TIME
        // TEMPLATE: KEY, VALUE, EXPIRATION TIME, TIME UNIT
        redisTemplate.opsForValue().set(redisKey, otp, OTP_VALIDITY_MINUTES, TimeUnit.MINUTES);

        log.info("Đã sinh OTP cho email: {}, OTP: {}, Hết hạn sau {} phút", email, otp, OTP_VALIDITY_MINUTES);
        return otp;
    }

}

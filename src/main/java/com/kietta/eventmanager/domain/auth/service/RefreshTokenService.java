package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.core.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final StringRedisTemplate redisTemplate;

    /**
     * Lưu JTI của refresh token vào family.
     * Key: rt_family:{familyId}
     * Value: {jti}
     */
    public void saveRefreshTokenToFamily(String familyId, String jti, long ttlMs) {
        redisTemplate.opsForValue().set(familyKey(familyId), jti, ttlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Lấy JTI hiện tại của family.
     */
    public String getRefreshTokenJtiFromFamily(String familyId) {
        return redisTemplate.opsForValue().get(familyKey(familyId));
    }

    /**
     * Check xem JTI có match hay không. Nếu không, có thể là hacker đang dùng token cũ.
     */
    public boolean isValidJtiForFamily(String familyId, String jti) {
        String storedJti = getRefreshTokenJtiFromFamily(familyId);
        return storedJti != null && storedJti.equals(jti);
    }

    /**
     * Revoke toàn bộ family (logout hoặc phát hiện theft).
     */
    public void revokeFamily(String familyId) {
        redisTemplate.delete(familyKey(familyId));
    }

    private String familyKey(String familyId) {
        return CacheConstants.REFRESH_TOKEN_FAMILY_PREFIX + familyId;
    }
}

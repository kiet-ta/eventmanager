package com.kietta.eventmanager.core.constant;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class CacheConstants {
    public static final String OTP_PREFIX = "otp:";
    public static final String OTP_FAILS_PREFIX = "otp_fails:";
    public static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    public static final String REFRESH_TOKEN_FAMILY_PREFIX = "rt_family:";  // Để track familyId
}

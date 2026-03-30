package com.kietta.eventmanager.core.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class để xử lý Cookie HttpOnly, Secure, SameSite
 * cho RefreshToken.
 */
@Component
@RequiredArgsConstructor
public class CookieUtils {
    @Value("${app.security.jwt.cookie-path:/api/v1/auth}")
    private String cookiePath;

    @Value("${app.security.jwt.cookie-domain:localhost}")
    private String cookieDomain;

    @Value("${app.security.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    /**
     * Set RefreshToken vào Cookie với HttpOnly, Secure, SameSite flags.
     *
     * @param response      HttpServletResponse để ghi cookie
     * @param refreshToken  Giá trị refresh token
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);           // Chặn JavaScript truy cập
        cookie.setSecure(isSecureContext());  // Chỉ gửi qua HTTPS ở production
        cookie.setPath(cookiePath);         // Giới hạn path
        cookie.setDomain(cookieDomain);     // Giới hạn domain
        cookie.setMaxAge((int) (refreshTokenExpirationMs / 1000)); // Thời gian sống
        cookie.setAttribute("SameSite", "Strict"); // Chống CSRF

        response.addCookie(cookie);
    }

    /**
     * Xóa RefreshToken cookie (logout).
     *
     * @param response HttpServletResponse để xóa cookie
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(isSecureContext());
        cookie.setPath(cookiePath);
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(0);  // Xóa ngay lập tức
        cookie.setAttribute("SameSite", "Strict");

        response.addCookie(cookie);
    }

    /**
     * Lấy tên cookie.
     */
    public static String getRefreshTokenCookieName() {
        return REFRESH_TOKEN_COOKIE_NAME;
    }

    /**
     * Check context có phải HTTPS không để quyết định flag Secure.
     */
    private boolean isSecureContext() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        return profile != null && profile.contains("prod");
    }
}

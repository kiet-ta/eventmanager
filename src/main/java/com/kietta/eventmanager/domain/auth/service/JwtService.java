package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    @Value("${app.security.jwt.secret-key}")
    private String secretKey;

    @Value("${app.security.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpiration;

    @Value("${app.security.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpiration;

    @Value("${app.security.jwt.register-expiration-ms:600000}")
    private long registerTokenExpiration;

    private SecretKey getSignInKey(){
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        } catch (IllegalArgumentException ignored) {
            return Keys.hmacShaKeyFor(secretKey.getBytes());
        }
    }

    public String generateAccessToken(User user, String email) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", email)
                .claim("role", user.getRole().name())
                .claim("purpose", "ACCESS")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId, String email, String familyId) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim("email", email)
                .claim("purpose", "REFRESH")
                .claim("familyId", familyId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public String generateRegisterToken(String email) {
        return Jwts.builder()
                .subject("register")
                .claim("email", email)
                .claim("purpose", "REGISTER")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + registerTokenExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public String extractRegisterEmail(String token) {
        Claims claims = parseClaims(token);
        String purpose = claims.get("purpose", String.class);
        if (!"REGISTER".equals(purpose)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Register token khong hop le");
        }
        return claims.get("email", String.class);
    }

    public RefreshTokenPayload extractRefreshTokenPayload(String token) {
        Claims claims = parseClaims(token);
        String purpose = claims.get("purpose", String.class);
        if (!"REFRESH".equals(purpose)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token khong hop le");
        }

        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        String jti = claims.getId();
        String familyId = claims.get("familyId", String.class);

        if (subject == null || email == null || jti == null || familyId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token khong hop le");
        }

        try {
            UUID userId = UUID.fromString(subject);
            return new RefreshTokenPayload(userId, email, jti, familyId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token khong hop le");
        }
    }

    public Claims extractAllClaims(String token) {
        return parseClaims(token);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token khong hop le");
        }
    }

    public record RefreshTokenPayload(UUID userId, String email, String jti, String familyId) {
    }
}

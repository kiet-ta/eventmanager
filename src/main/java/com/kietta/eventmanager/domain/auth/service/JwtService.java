package com.kietta.eventmanager.domain.auth.service;

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

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpiration;

    @Value("${app.security.jwt.register-expiration-ms:600000}")
    private long registerTokenExpiration;

    private SecretKey getSignInKey(){
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        } catch (IllegalArgumentException ignored) {
            return Keys.hmacShaKeyFor(secretKey.getBytes());
        }
    }

    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
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

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Register token khong hop le");
        }
    }
}

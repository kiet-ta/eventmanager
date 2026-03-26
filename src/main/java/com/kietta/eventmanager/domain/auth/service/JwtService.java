package com.kietta.eventmanager.domain.auth.service;

import com.kietta.eventmanager.domain.user_identities.repository.UserIdentityRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    @Value("${app.security.jwt.secret-key}")
    private String secretKey;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpiration;

    private SecretKey getSignInKey(){
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);// If your secret string is plain text, you can use getBytes() directly,
        // but Base64 encoding is more appropriate. Here I'm handling a plain string.
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(userId.toString()) // Subject usually stores the user's ID
                .claim("email", email) // Add additional data (called Claims)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }
}

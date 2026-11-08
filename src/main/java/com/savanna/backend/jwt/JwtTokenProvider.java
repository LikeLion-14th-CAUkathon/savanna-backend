package com.savanna.backend.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT 서명 키는 최소 256비트여야 합니다."
            );
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // Access Token 생성
    public String createAccessToken(Long userId) {
        return createToken(
                userId, "ACCESS", accessExpiration
        );
    }

    // Refresh Token 생성
    public String createRefreshToken(Long userId) {
        return createToken(
                userId, "REFRESH", refreshExpiration
        );
    }

    private String createToken(
            Long userId,
            String type,
            long expiration
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusMillis(expiration)
                ))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // JWT 서명과 만료 여부 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // JWT 사용자 ID 추출
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);

        return Long.valueOf(claims.getSubject());
    }

    // 토큰 종류 확인
    public boolean isAccessToken(String token) {
        return hasType(token, "ACCESS");
    }

    public boolean isRefreshToken(String token) {
        return hasType(token, "REFRESH");
    }

    private boolean hasType(String token, String type) {
        try {
            Claims claims = parseClaims(token);
            return type.equals(
                    claims.get("type", String.class)
            );
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Refresh Token 원문을 SHA-256 해시로 변환
    public String hashToken(String token) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 해시 알고리즘을 사용할 수 없습니다.",
                    e
            );
        }
    }
}

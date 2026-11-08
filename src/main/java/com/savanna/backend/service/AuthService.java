package com.savanna.backend.service;

import com.savanna.backend.domain.RefreshToken;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.RefreshTokenRepository;
import com.savanna.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public TokenResult refresh(String oldRefreshToken) {

        // 1. JWT 검증 및 종류 확인
        if (!jwtTokenProvider.isRefreshToken(oldRefreshToken)) {
            throw unauthorized();
        }

        Long userId;
        try {
            userId = jwtTokenProvider.getUserId(oldRefreshToken);
        } catch (RuntimeException e) {
            throw unauthorized();
        }

        // 2. DB에서 기존 Refresh Token 조회
        String oldHash = jwtTokenProvider.hashToken(oldRefreshToken);

        RefreshToken savedToken = refreshTokenRepository
                .findByTokenHash(oldHash)
                .orElseThrow(this::unauthorized);

        // 3. 사용자 일치 및 토큰 유효 상태 확인
        if (!savedToken.isActive()
                || !savedToken.getUser().getUserId().equals(userId)) {
            throw unauthorized();
        }

        var user = userRepository.findById(userId)
                .orElseThrow(this::unauthorized);

        // 4. 새 토큰 발급
        String newAccessToken =
                jwtTokenProvider.createAccessToken(userId);

        String newRefreshToken =
                jwtTokenProvider.createRefreshToken(userId);

        // 5. 기존 토큰 무효화
        savedToken.revoke();

        // 6. 새 Refresh Token 해시 저장
        RefreshToken newToken = new RefreshToken(
                user,
                jwtTokenProvider.hashToken(newRefreshToken),
                Instant.now().plusMillis(refreshExpiration)
        );

        refreshTokenRepository.save(newToken);

        return new TokenResult(
                newAccessToken,
                newRefreshToken
        );
    }

    // 로그아웃: Refresh Token 무효화
    @Transactional
    public void logout(String refreshToken) {

        // 쿠키가 없으면 이미 로그아웃된 상태로 처리
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // 전달받은 토큰을 해시로 변환
        String tokenHash = jwtTokenProvider.hashToken(refreshToken);

        // DB에서 해당 토큰 조회
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(savedToken -> {

                    // 아직 무효화되지 않은 토큰만 처리
                    if (savedToken.getRevokedAt() == null) {
                        savedToken.revoke();
                    }
                });
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "유효하지 않은 Refresh Token입니다."
        );
    }

    public record TokenResult(
            String accessToken,
            String refreshToken
    ) {}
}

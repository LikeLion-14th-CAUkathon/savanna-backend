package com.savanna.backend.handler;

import com.savanna.backend.domain.RefreshToken;
import com.savanna.backend.domain.User;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.RefreshTokenRepository;
import com.savanna.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OidcUser oidcUser =
                (OidcUser) authentication.getPrincipal();

        User user = userRepository
                .findByGoogleSub(oidcUser.getSubject())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // JWT 발급
        String refreshToken =
                jwtTokenProvider.createRefreshToken(
                        user.getUserId()
                );

        // Refresh Token 해시 저장
        String tokenHash =
                jwtTokenProvider.hashToken(refreshToken);

        Instant expiresAt =
                Instant.now().plus(Duration.ofDays(14));

        RefreshToken savedToken = new RefreshToken(
                user,
                tokenHash,
                expiresAt
        );

        refreshTokenRepository.save(savedToken);

        // Refresh Token을 HttpOnly 쿠키로 전달
        ResponseCookie cookie = ResponseCookie
                .from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(14))
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        // 임시 로그인 성공 페이지
        response.sendRedirect("/login-success");
    }
}

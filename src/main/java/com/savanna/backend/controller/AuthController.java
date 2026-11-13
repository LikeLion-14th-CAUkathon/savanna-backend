package com.savanna.backend.controller;

import com.savanna.backend.service.AuthService;
import com.savanna.backend.config.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookies;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refresh(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            ) String refreshToken,
            HttpServletResponse response
    ) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh Token이 없습니다."
            );
        }

        // Refresh Token 검증 및 재발급
        AuthService.TokenResult result =
                authService.refresh(refreshToken);

        // 새 Refresh Token 쿠키 설정
        ResponseCookie cookie = authCookies.refreshToken(result.refreshToken());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        // 새 Access Token 반환
        return ResponseEntity.ok(
                new ApiResponse(
                        200,
                        "토큰 재발급 성공",
                        new TokenData(
                                result.accessToken(),
                                "Bearer",
                                accessExpiration / 1000
                        )
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            ) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // DB의 Refresh Token 무효화
        authService.logout(refreshToken);

        // OAuth2 로그인에 사용된 세션도 종료
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        // 브라우저의 Refresh Token 쿠키 삭제
        ResponseCookie cookie = authCookies.clearRefreshToken();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "로그아웃 성공"
                )
        );
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(
            org.springframework.security.web.csrf.CsrfToken token
    ) {
        return ResponseEntity.ok(
                java.util.Map.of("csrfToken", token.getToken())
        );
    }

    public record ApiResponse(
            int status,
            String message,
            TokenData data
    ) {}

    public record TokenData(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {}
}

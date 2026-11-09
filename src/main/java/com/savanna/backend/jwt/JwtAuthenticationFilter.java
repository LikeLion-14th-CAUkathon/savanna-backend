package com.savanna.backend.jwt;

import com.savanna.backend.domain.User;
import com.savanna.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization =
                request.getHeader("Authorization");

        // Authorization 헤더가 없으면 다음 필터로 이동
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Bearer 형식 확인
        if (!authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }

        String token = authorization.substring(7);

        // JWT 유효성 및 Access Token 종류 확인
        if (!jwtTokenProvider.isAccessToken(token)) {
            unauthorized(response);
            return;
        }

        Long userId;

        try {
            userId = jwtTokenProvider.getUserId(token);
        } catch (RuntimeException e) {
            unauthorized(response);
            return;
        }

        // DB에서 사용자 조회
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            unauthorized(response);
            return;
        }

        // Spring Security 인증 객체 생성
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                );

        // SecurityContext에 인증 정보 등록
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private void unauthorized(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write(
                "{\"status\":401,"
                        + "\"message\":\"유효하지 않은 Access Token입니다.\","
                        + "\"data\":null}"
        );
    }
}

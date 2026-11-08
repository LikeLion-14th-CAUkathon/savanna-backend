package com.savanna.backend.repository;

import com.savanna.backend.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    // 토큰 해시로 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}

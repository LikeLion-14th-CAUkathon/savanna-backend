package com.savanna.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "team_invitation")
public class TeamInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invitationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_seed", length = 36)
    private String tokenSeed;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    public TeamInvitation(
            Team team,
            User createdBy,
            String tokenHash
    ) {
        this.team = team;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(
                7L * 24 * 60 * 60
        );
    }

    public TeamInvitation(
            Team team,
            User createdBy,
            String tokenHash,
            String tokenSeed
    ) {
        this.team = team;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.tokenSeed = tokenSeed;
        this.createdAt = Instant.now();

        // 기존 DB의 만료일 컬럼을 유지하기 위한 값
        // 실제 영구 링크 여부는 tokenSeed로 구분
        this.expiresAt = this.createdAt.plusSeconds(
                7L * 24 * 60 * 60
        );
    }

    public boolean isActive() {
        if (revokedAt != null) {
            return false;
        }

        // 새 영구 고정 링크는 만료일을 적용하지 않음
        if (tokenSeed != null) {
            return true;
        }

        // 기존 7일 만료 링크는 기존 정책 유지
        return Instant.now().isBefore(expiresAt);
    }

    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }
}

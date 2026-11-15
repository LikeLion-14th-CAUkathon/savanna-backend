package com.savanna.backend.dto.response;

import com.savanna.backend.domain.TeamInvitation;

import java.time.Instant;

public record TeamInvitationListResponseDto(
        Long invitationId,
        Long teamId,
        Instant createdAt,
        @io.swagger.v3.oas.annotations.media.Schema(description="과거 7일형의 만료 시각. 영구 링크는 null",nullable=true)
        Instant expiresAt,
        Instant revokedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description="ACTIVE/REVOKED/EXPIRED. EXPIRED는 과거 7일형에만 발생")
        com.savanna.backend.enums.TeamInvitationStatus status,
        @io.swagger.v3.oas.annotations.media.Schema(description="활성 영구 링크만 복원 가능. 폐기·만료·과거 해시 전용 링크는 null",nullable=true)
        String token,
        @io.swagger.v3.oas.annotations.media.Schema(description="복원 가능한 활성 영구 링크의 프런트 공유 URL. 그 외 null",nullable=true)
        String inviteUrl
) {
    public static TeamInvitationListResponseDto from(
            TeamInvitation invitation
    ) {
        return from(invitation,null,null);
    }
    public static TeamInvitationListResponseDto from(TeamInvitation invitation,String token,String inviteUrl) {

        return new TeamInvitationListResponseDto(
                invitation.getInvitationId(),
                invitation.getTeam().getTeamId(),
                invitation.getCreatedAt(),
                invitation.getTokenSeed() != null
                        ? null
                        : invitation.getExpiresAt(),
                invitation.getRevokedAt(),
                invitation.getStatus(),token,inviteUrl
        );
    }
}

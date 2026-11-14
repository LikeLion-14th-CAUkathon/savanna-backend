package com.savanna.backend.dto.response;

import com.savanna.backend.domain.TeamInvitation;

import java.time.Instant;

public record TeamInvitationListResponseDto(
        Long invitationId,
        Long teamId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String status
) {
    public static TeamInvitationListResponseDto from(
            TeamInvitation invitation
    ) {
        String status;

        if (invitation.getRevokedAt() != null) {
            status = "REVOKED";
        } else if (!invitation.isActive()) {
            status = "EXPIRED";
        } else {
            status = "ACTIVE";
        }

        return new TeamInvitationListResponseDto(
                invitation.getInvitationId(),
                invitation.getTeam().getTeamId(),
                invitation.getCreatedAt(),
                invitation.getTokenSeed() != null
                        ? null
                        : invitation.getExpiresAt(),
                invitation.getRevokedAt(),
                status
        );
    }
}

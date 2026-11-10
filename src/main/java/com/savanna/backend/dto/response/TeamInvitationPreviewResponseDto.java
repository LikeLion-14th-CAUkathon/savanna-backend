package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamInvitation;

import java.time.Instant;

public record TeamInvitationPreviewResponseDto(
        Long teamId,
        String teamName,
        String projectName,
        String projectIntro,
        long currentMemberCount,
        long expectedMemberCount,
        Instant expiresAt
) {
    public static TeamInvitationPreviewResponseDto from(
            TeamInvitation invitation,
            long currentMemberCount
    ) {
        Team team = invitation.getTeam();

        return new TeamInvitationPreviewResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                team.getProjectIntro(),
                currentMemberCount,
                currentMemberCount + 1,
                invitation.getTokenSeed() != null
                        ? null
                        : invitation.getExpiresAt()
        );
    }
}

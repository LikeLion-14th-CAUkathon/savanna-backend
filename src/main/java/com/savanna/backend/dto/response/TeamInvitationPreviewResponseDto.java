package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamInvitation;

import java.time.Instant;

public record TeamInvitationPreviewResponseDto(
        Long teamId,
        String teamName,
        String projectName,
        String projectIntro,
        Instant expiresAt
) {
    public static TeamInvitationPreviewResponseDto from(
            TeamInvitation invitation
    ) {
        Team team = invitation.getTeam();

        return new TeamInvitationPreviewResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                team.getProjectIntro(),
                invitation.getExpiresAt()
        );
    }
}

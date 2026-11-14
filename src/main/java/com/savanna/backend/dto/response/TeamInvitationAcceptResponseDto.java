package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.enums.TeamRole;

public record TeamInvitationAcceptResponseDto(
        Long teamId,
        String teamName,
        TeamRole role
) {
    public static TeamInvitationAcceptResponseDto from(
            Team team
    ) {
        return new TeamInvitationAcceptResponseDto(
                team.getTeamId(),
                team.getName(),
                TeamRole.MEMBER
        );
    }
}

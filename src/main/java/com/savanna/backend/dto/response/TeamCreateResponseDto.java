package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.enums.TeamRole;

public record TeamCreateResponseDto(
        Long teamId,
        String name,
        TeamRole role
) {
    public static TeamCreateResponseDto from(
            Team team,
            TeamRole role
    ) {
        return new TeamCreateResponseDto(
                team.getTeamId(),
                team.getName(),
                role
        );
    }
}

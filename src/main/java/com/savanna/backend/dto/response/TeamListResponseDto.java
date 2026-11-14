package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.enums.TeamRole;

public record TeamListResponseDto(
        Long teamId,
        String name,
        String projectName,
        TeamRole role
) {
    public static TeamListResponseDto from(TeamMember member) {
        Team team = member.getTeam();

        return new TeamListResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                member.getRole()
        );
    }
}

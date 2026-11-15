package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.enums.TeamRole;

public record TeamCreateResponseDto(
        Long teamId,
        String name,
        TeamRole role,
        @io.swagger.v3.oas.annotations.media.Schema(description="팀 프로필 사진 URL. 미설정은 null",nullable=true)
        String photoUrl
) {
    public static TeamCreateResponseDto from(
            Team team,
            TeamRole role
    ) {
        return from(team, role, team.getPhotoUrl());
    }
    public static TeamCreateResponseDto from(Team team, TeamRole role, String photoUrl) {
        return new TeamCreateResponseDto(
                team.getTeamId(),
                team.getName(),
                role,
                photoUrl
        );
    }
}

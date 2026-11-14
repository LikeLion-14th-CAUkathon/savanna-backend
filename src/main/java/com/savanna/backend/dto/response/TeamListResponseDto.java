package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.enums.TeamRole;

public record TeamListResponseDto(
        Long teamId,
        String name,
        String projectName,
        TeamRole role,
        @io.swagger.v3.oas.annotations.media.Schema(description="팀 프로필 사진 URL. 미설정은 null",nullable=true)
        String photoUrl
) {
    public static TeamListResponseDto from(TeamMember member) {
        return from(member, member.getTeam().getPhotoUrl());
    }
    public static TeamListResponseDto from(TeamMember member, String photoUrl) {
        Team team = member.getTeam();

        return new TeamListResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                member.getRole(),
                photoUrl
        );
    }
}

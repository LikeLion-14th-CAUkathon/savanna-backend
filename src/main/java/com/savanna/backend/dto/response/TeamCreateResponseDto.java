package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.enums.TeamRole;

public record TeamCreateResponseDto(
        Long teamId,
        String name,
        TeamRole role,
        @io.swagger.v3.oas.annotations.media.Schema(description="팀 프로필 사진 URL. 미설정은 null",nullable=true)
        String photoUrl,
        @io.swagger.v3.oas.annotations.media.Schema(description="팀 생성과 같은 트랜잭션에서 발급된 고정 초대 링크. 팀원은 현재 초대 링크 조회 API로 같은 주소를 다시 조회할 수 있습니다.")
        String inviteUrl
) {
    public static TeamCreateResponseDto from(
            Team team,
            TeamRole role
    ) {
        return from(team, role, team.getPhotoUrl());
    }
    public static TeamCreateResponseDto from(Team team, TeamRole role, String photoUrl) {
        return from(team,role,photoUrl,null);
    }
    public static TeamCreateResponseDto from(Team team, TeamRole role, String photoUrl,String inviteUrl) {
        return new TeamCreateResponseDto(
                team.getTeamId(),
                team.getName(),
                role,
                photoUrl,inviteUrl
        );
    }
}

package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.enums.TeamRole;

import java.util.List;

public record TeamDetailResponseDto(
        Long teamId,
        String name,
        String projectName,
        String projectIntro,
        String githubUrl,
        String presentationUrl,
        String stack,
        List<MemberInfo> members,
        @io.swagger.v3.oas.annotations.media.Schema(description="팀 프로필 사진 URL. 미설정은 null",nullable=true)
        String photoUrl
) {
    public record MemberInfo(
            Long userId,
            String name,
            TeamRole role,
            @io.swagger.v3.oas.annotations.media.Schema(description="팀원의 선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
            Integer characterId
    ) {
        public static MemberInfo from(TeamMember member) {
            return new MemberInfo(
                    member.getUser().getUserId(),
                    member.getUser().getName(),
                    member.getRole(),
                    member.getUser().getCharacterId()
            );
        }
    }

    public static TeamDetailResponseDto from(
            Team team,
            List<TeamMember> members
    ) {
        return from(team, members, team.getPhotoUrl());
    }
    public static TeamDetailResponseDto from(Team team, List<TeamMember> members, String photoUrl) {
        return new TeamDetailResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                team.getProjectIntro(),
                team.getGithubUrl(),
                team.getPresentationUrl(),
                team.getStack(),
                members.stream()
                        .map(MemberInfo::from)
                        .toList(),
                photoUrl
        );
    }
}

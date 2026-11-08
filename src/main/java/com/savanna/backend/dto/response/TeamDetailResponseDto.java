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
        List<MemberInfo> members
) {
    public record MemberInfo(
            Long userId,
            String name,
            TeamRole role
    ) {
        public static MemberInfo from(TeamMember member) {
            return new MemberInfo(
                    member.getUser().getUserId(),
                    member.getUser().getName(),
                    member.getRole()
            );
        }
    }

    public static TeamDetailResponseDto from(
            Team team,
            List<TeamMember> members
    ) {
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
                        .toList()
        );
    }
}

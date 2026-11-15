package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamInvitation;
import com.savanna.backend.domain.TeamMember;

import java.time.Instant;
import java.util.List;

public record TeamInvitationPreviewResponseDto(
        Long teamId,
        String teamName,
        String projectName,
        String projectIntro,
        long currentMemberCount,
        long expectedMemberCount,
        Instant expiresAt,
        @io.swagger.v3.oas.annotations.media.Schema(description="현재 팀 소속 구성원 목록. 탈퇴자는 제외하며 팀원이 없으면 빈 배열")
        List<InvitationMember> members
) {
    public record InvitationMember(
            String name,
            @io.swagger.v3.oas.annotations.media.Schema(description="현재 선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
            Integer characterId
    ) {}
    public static TeamInvitationPreviewResponseDto from(
            TeamInvitation invitation,
            List<TeamMember> teamMembers
    ) {
        Team team = invitation.getTeam();
        var members=teamMembers.stream()
                .sorted(java.util.Comparator.comparing(TeamMember::getJoinedAt).thenComparing(TeamMember::getTeamMemberId))
                .map(member -> new InvitationMember(member.getUser().getName(),member.getUser().getCharacterId()))
                .toList();

        return new TeamInvitationPreviewResponseDto(
                team.getTeamId(),
                team.getName(),
                team.getProjectName(),
                team.getProjectIntro(),
                members.size(),
                members.size() + 1,

        invitation.getTokenSeed() != null
                ? null
                : invitation.getExpiresAt(),
                members

        );
    }
}

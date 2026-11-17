package com.savanna.backend.service;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.domain.User;
import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.dto.request.TeamUpdateRequestDto;
import com.savanna.backend.dto.response.TeamCreateResponseDto;
import com.savanna.backend.dto.response.TeamDetailResponseDto;
import com.savanna.backend.dto.response.TeamLeaveResponseDto;
import com.savanna.backend.dto.response.TeamListResponseDto;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.repository.ProposalRecipientRepository;
import com.savanna.backend.repository.ProposalRepository;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamPhotoService teamPhotos;
    private final TeamMembershipPolicy membershipPolicy;
    private final TeamInvitationService invitations;
    private final TeamDeletionService deletion;
    private final ProposalRecipientRepository proposalRecipientRepository;
    private final ProposalRepository proposalRepository;

    @Transactional
    public TeamCreateResponseDto createTeam(
            Long userId,
            TeamCreateRequestDto request
    ) {
        User user = membershipPolicy.lockUser(userId);
        membershipPolicy.requireNoTeam(userId);

        if (!user.isOnboardingCompleted()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "온보딩을 완료해야 팀을 생성할 수 있습니다."
            );
        }

        Team team = new Team(
                request.name().trim(),
                request.projectName().trim(),
                request.projectIntro().trim(),
                normalizeOptional(request.githubUrl()),
                normalizeOptional(request.presentationUrl()),
                normalizeOptional(request.stack())
        );

        teamPhotos.applyLegacyInput(team, request.photoUrl());
        teamRepository.save(team);

        TeamMember teamMember = new TeamMember(
                user,
                team,
                TeamRole.LEADER
        );

        teamMemberRepository.save(teamMember);

        var invitation = invitations.createInvitation(userId, team.getTeamId());

        return TeamCreateResponseDto.from(
                team,
                TeamRole.LEADER,
                teamPhotos.url(team),
                invitation.inviteUrl()
        );
    }

    @Transactional(readOnly = true)
    public List<TeamListResponseDto> getMyTeams(Long userId) {
        return teamMemberRepository
                .findAllByUser_UserId(userId)
                .stream()
                .map(member -> TeamListResponseDto.from(member, teamPhotos.url(member.getTeam())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponseDto getTeamDetail(
            Long userId,
            Long teamId
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "팀을 찾을 수 없습니다."
                        )
                );

        boolean isMember = teamMemberRepository
                .existsByUser_UserIdAndTeam_TeamId(
                        userId,
                        teamId
                );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 팀에 접근할 권한이 없습니다."
            );
        }

        List<TeamMember> members = teamMemberRepository
                .findAllByTeam_TeamId(teamId);

        return TeamDetailResponseDto.from(team, members, teamPhotos.url(team));
    }

    @Transactional
    public TeamDetailResponseDto updateTeam(
            Long userId,
            Long teamId,
            TeamUpdateRequestDto request
    ) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "팀을 찾을 수 없습니다."
                        )
                );

        TeamMember member = teamMemberRepository
                .findByUser_UserIdAndTeam_TeamId(userId, teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "해당 팀에 접근할 권한이 없습니다."
                        )
                );

        if (member.getRole() != TeamRole.LEADER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "팀장만 팀 정보를 수정할 수 있습니다."
            );
        }

        try {
            team.update(
                    request.name(),
                    request.projectName(),
                    request.projectIntro(),
                    request.githubUrl(),
                    request.presentationUrl(),
                    request.stack()
            );
            if (request.photoUrl() != null) {
                teamPhotos.applyLegacyInput(team, request.photoUrl());
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }

        var members = teamMemberRepository
                .findAllByTeam_TeamId(teamId);

        return TeamDetailResponseDto.from(team, members, teamPhotos.url(team));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public com.savanna.backend.dto.response.TeamLeaveResponseDto leaveTeam(Long userId, Long teamId) {
        membershipPolicy.lockUser(userId);
        if (teamId == null || teamId <= 0) {
            throw new com.savanna.backend.error.BusinessException(com.savanna.backend.error.ErrorCode.INVALID_REQUEST);
        }
        teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new com.savanna.backend.error.BusinessException(com.savanna.backend.error.ErrorCode.RESOURCE_NOT_FOUND));
        var member = teamMemberRepository.findByUser_UserIdAndTeam_TeamId(userId, teamId)
                .orElseThrow(() -> new com.savanna.backend.error.BusinessException(com.savanna.backend.error.ErrorCode.FORBIDDEN));
        var remaining = teamMemberRepository.findAllByTeam_TeamId(teamId).stream()
                .filter(m -> !m.getUser().getUserId().equals(userId))
                .sorted(java.util.Comparator.comparing(TeamMember::getJoinedAt).thenComparing(TeamMember::getTeamMemberId))
                .toList();
        if (remaining.isEmpty()) {
            deletion.deleteTeam(teamId);
            return new com.savanna.backend.dto.response.TeamLeaveResponseDto(teamId, userId, null, true);
        }
        Long newLeader = null;
        if (member.getRole() == TeamRole.LEADER) {
            var successor = remaining.get(0);
            successor.promoteToLeader();
            newLeader = successor.getUser().getUserId();
        }

        // 1. 탈퇴자 본인이 해당 팀에서 받은 모든 제안 응답만 삭제 (다른 팀원의 응답은 유지)
        proposalRecipientRepository.deleteAllByUserAndTeam(userId, teamId);

        // 2. 탈퇴자가 해당 팀에 보낸 제안의 발신자만 null 처리 (제안 및 남은 팀원 응답·오아시스 공고 유지)
        proposalRepository.detachSenderByUserAndTeam(userId, teamId);

        // 3. 팀 소속 삭제
        teamMemberRepository.delete(member);
        teamMemberRepository.flush();

        return new TeamLeaveResponseDto(teamId, userId, newLeader, false);
    }
}
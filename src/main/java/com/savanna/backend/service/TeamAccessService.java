package com.savanna.backend.service;

import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.repository.TeamRepository;
import com.savanna.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamAccessService implements ExpeditionTeamAccess, EligibilityTeamReader, ProposalTeamAccess {
    private final TeamMemberRepository members;
    private final TeamRepository teams;
    private final UserRepository users;

    private Long authenticatedUserId(Principal principal) {
        // JWT 필터가 검증한 Long principal만 사용합니다. getName()은 사용자 ID로 파싱하지 않습니다.
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)
                || userId <= 0 || !users.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireMembership(Long userId, Long teamId) {
        if (teamId == null || teamId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!teams.existsById(teamId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!members.existsByUser_UserIdAndTeam_TeamId(userId, teamId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    public Long requireMember(Principal principal, Long teamId, boolean write) {
        Long userId = authenticatedUserId(principal);
        // 원정은 팀원 공동 작업 공간입니다. 팀 정보 수정의 팀장 제한과 별개입니다.
        requireMembership(userId, teamId);
        return userId;
    }

    private Optional<Long> onlyTeam(Long userId) {
        List<TeamMember> memberships = members.findAllByUser_UserId(userId);
        return memberships.size() == 1
                ? Optional.of(memberships.get(0).getTeam().getTeamId()) : Optional.empty();
    }

    @Override
    public Optional<TeamSnapshot> read(Principal principal, Long teamId) {
        Long userId = authenticatedUserId(principal);
        if (teamId == null) {
            var selected = onlyTeam(userId);
            if (selected.isEmpty()) return Optional.empty();
            teamId = selected.get();
        }
        requireMembership(userId, teamId);
        return Optional.of(new TeamSnapshot(teamId, members.findAllByTeam_TeamId(teamId).stream()
                .map(member -> member.getUser().getEnrollmentStatus()).toList()));
    }

    @Override
    public ProposalTeamContext requireTeam(Principal principal) {
        return requireTeam(principal, null);
    }

    @Override
    public ProposalTeamContext requireTeam(Principal principal, Long teamId) {
        Long userId = authenticatedUserId(principal);
        if (teamId == null) {
            teamId = onlyTeam(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
        }
        requireMembership(userId, teamId);
        return new ProposalTeamContext(teamId, userId, members.findAllByTeam_TeamId(teamId).stream()
                .map(member -> member.getUser().getUserId()).filter(id -> !id.equals(userId)).toList());
    }
}

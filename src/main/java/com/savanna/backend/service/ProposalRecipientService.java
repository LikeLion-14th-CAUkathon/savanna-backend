package com.savanna.backend.service;

import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.dto.response.*;
import com.savanna.backend.enums.ProposalRecipientStatus;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalRecipientService {
    private final ProposalRecipientRepository recipients;
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0 || !users.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public List<ReceivedProposalResponse> received(Long userId) {
        requireUser(userId);
        return recipients.findReceivedProposals(userId).stream().map(ReceivedProposalResponse::from).toList();
    }

    public ProposalPendingCountResponse pendingCount(Long userId) {
        requireUser(userId);
        return new ProposalPendingCountResponse(recipients.countByUser_UserIdAndStatus(userId, ProposalRecipientStatus.PENDING));
    }

    @Transactional
    public ProposalRecipientResponse respond(Long userId, Long recipientId, ProposalRecipientStatus status) {
        requireUser(userId);
        if (recipientId == null || recipientId <= 0 || status == null || status == ProposalRecipientStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var recipient = recipients.findForResponse(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!recipient.getUser().getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        // 수신 기록을 잠가 동시에 온 응답도 한 번만 처리합니다.
        recipient.respond(status);
        if (status == ProposalRecipientStatus.JOINED) {
            Long teamId = recipient.getProposal().getExpedition().getTeamId();
            var team = teams.findForProposalResponse(teamId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            // 이미 팀원인 수신자는 역할을 유지하고, 새 가입자만 MEMBER로 등록합니다.
            if (!members.existsByUser_UserIdAndTeam_TeamId(userId, teamId)) {
                if (!recipient.getUser().isOnboardingCompleted()) throw new BusinessException(ErrorCode.FORBIDDEN);
                members.save(new TeamMember(recipient.getUser(), team, TeamRole.MEMBER));
            }
        }
        return ProposalRecipientResponse.from(recipient);
    }
}

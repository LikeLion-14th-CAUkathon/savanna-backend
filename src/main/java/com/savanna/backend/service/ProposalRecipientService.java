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
    public ReceivedProposalResponse markAsRead(Long userId, Long recipientId) {
        requireUser(userId);
        if (recipientId == null || recipientId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var recipient = recipients.findForResponse(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!recipient.getUser().getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        recipient.markAsRead();
        return ReceivedProposalResponse.from(recipient);
    }

    public ProposalNotificationResponse notifications(Long userId) {
        requireUser(userId);
        long unreadProposals = recipients.countByUser_UserIdAndReadAtIsNull(userId);
        long unreadResponses = recipients.countUnreadResponses(userId);
        return new ProposalNotificationResponse(unreadProposals > 0 || unreadResponses > 0, unreadProposals, unreadResponses);
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
        // 응답 변경·읽음·버전 갱신을 같은 수신 기록 잠금 아래 처리합니다.
        boolean changed = recipient.respond(status);
        if (changed && status == ProposalRecipientStatus.JOINED) {
            Long teamId = recipient.getProposal().getExpedition().getTeamId();
            var team = teams.findForProposalResponse(teamId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            // 이미 팀원인 수신자는 역할을 유지하고, 새 가입자만 MEMBER로 등록합니다.
            if (!members.existsByUser_UserIdAndTeam_TeamId(userId, teamId)) {
                if (!recipient.getUser().isOnboardingCompleted()) throw new BusinessException(ErrorCode.FORBIDDEN);
                members.save(new TeamMember(recipient.getUser(), team, TeamRole.MEMBER));
            }
        }
        // DECLINED로 변경해도 팀 소속·역할은 유지합니다. 원정 합류 집계는 현재 status로 계산합니다.
        return ProposalRecipientResponse.from(recipient);
    }
}

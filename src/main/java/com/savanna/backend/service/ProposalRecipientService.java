package com.savanna.backend.service;

import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.dto.response.*;
import com.savanna.backend.enums.ProposalRecipientStatus;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalRecipientService {
    private final ProposalRecipientRepository recipients;
    private final ProposalRecipientSyncService recipientSync;
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TeamMembershipPolicy membershipPolicy;
    private final EntityManager em;

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
        return new ProposalPendingCountResponse(
                recipients.countByUserAndCurrentTeamAndStatus(
                        userId, ProposalRecipientStatus.PENDING)
        );
    }

    public ProposalNotificationResponse notifications(Long userId) {
        requireUser(userId);
        long unreadProposals = recipients.countUnreadProposalsInCurrentTeam(userId);
        long unreadResponses = recipients.countUnreadResponses(userId);
        return new ProposalNotificationResponse(unreadProposals > 0 || unreadResponses > 0, unreadProposals, unreadResponses);
    }

    @Transactional
    public ProposalRecipientResponse respond(Long userId, Long recipientId, ProposalRecipientStatus status) {
        requireUser(userId);
        membershipPolicy.lockUser(userId);
        if (recipientId == null || recipientId <= 0 || status == null || status == ProposalRecipientStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var snapshot = recipients.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!snapshot.getUser().getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        var lockedTeam = teams.findForProposalResponse(snapshot.getProposal().getExpedition().getTeamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var recipient = recipients.findForResponse(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        em.refresh(recipient, LockModeType.PESSIMISTIC_WRITE);
        if (!recipient.getUser().getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (recipient.isRevoked()) throw new BusinessException(ErrorCode.FORBIDDEN);
        Long targetTeamId = recipient.getProposal()
                .getExpedition()
                .getTeamId();

        membershipPolicy.requireOnlyTeam(userId, targetTeamId);
        // 같은 수신 기록 잠금 아래 응답 변경·읽음·버전 갱신 처리
        boolean changed = recipient.respond(status);
        if (changed && status == ProposalRecipientStatus.JOINED) {
            // 기존 팀원의 역할은 유지하고 새 가입자만 MEMBER로 등록
            if (!members.existsByUser_UserIdAndTeam_TeamId(userId, targetTeamId)) {
                if (!recipient.getUser().isOnboardingCompleted()) throw new BusinessException(ErrorCode.FORBIDDEN);
                members.save(new TeamMember(recipient.getUser(), lockedTeam, TeamRole.MEMBER));

                recipientSync.registerExistingProposals(targetTeamId, recipient.getUser());
            }
        }
        // DECLINED 변경 시 팀 소속·역할 유지, 원정 합류 인원은 현재 status로 집계
        return ProposalRecipientResponse.from(recipient);
    }

    @Transactional
    public ReceivedProposalResponse markAsRead(
            Long userId,
            Long recipientId
    ) {
        // 1. 로그인 사용자 확인
        requireUser(userId);

        // 2. 요청 ID 검증
        if (recipientId == null || recipientId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 제안 수신 기록 조회
        var recipient = recipients.findForResponse(recipientId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        // 4. 본인이 받은 제안인지 확인
        if (!recipient.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 5. 현재 해당 팀에 소속되어 있는지 확인
        Long targetTeamId = recipient.getProposal()
                .getExpedition()
                .getTeamId();

        if (recipient.isRevoked() || !members.existsByUser_UserIdAndTeam_TeamId(userId, targetTeamId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 6. 읽음 처리
        recipient.markAsRead();

        // 7. 결과 반환
        return ReceivedProposalResponse.from(recipient);
    }
}

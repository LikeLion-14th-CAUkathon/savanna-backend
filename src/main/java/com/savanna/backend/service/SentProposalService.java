package com.savanna.backend.service;

import com.savanna.backend.dto.response.*;
import com.savanna.backend.dto.request.ProposalResponsesReadRequest;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SentProposalService {
    private final ProposalRepository proposals;
    private final ProposalRecipientRepository recipients;
    private final UserRepository users;
    private final TeamMemberRepository members;
    private final TeamRepository teams;
    private final TeamMembershipPolicy membershipPolicy;

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0 || !users.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public List<SentProposalResponse> sent(Long userId) {
        requireUser(userId);
        return proposals.findAllBySender_UserIdOrderByCreatedAtDescProposalIdDesc(userId).stream()
                .map(SentProposalResponse::from).toList();
    }

    public ProposalRecipientsResponse recipients(Long userId, Long proposalId) {
        requireUser(userId);
        if (proposalId == null || proposalId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (proposal.getSender() == null || !proposal.getSender().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!members.existsByUser_UserIdAndTeam_TeamId(userId, proposal.getExpedition().getTeamId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 같은 조회 결과로 집계와 개인별 상태 구성
        return ProposalRecipientsResponse.from(proposalId,
                recipients.findAllByProposal_ProposalIdOrderByProposalRecipientIdAsc(proposalId));
    }

    @Transactional
    public ProposalResponsesReadResponse markResponsesRead(Long userId, Long proposalId, ProposalResponsesReadRequest request) {
        requireUser(userId);
        membershipPolicy.lockUser(userId);
        if (proposalId == null || proposalId <= 0 || request == null || request.responses() == null
                || request.responses().isEmpty() || request.responses().size() > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var ids = new java.util.HashSet<Long>();
        for (var response : request.responses()) {
            if (response == null || response.proposalRecipientId() == null || response.proposalRecipientId() <= 0
                    || response.responseVersion() <= 0 || !ids.add(response.proposalRecipientId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        var proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (proposal.getSender() == null || !proposal.getSender().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Long teamId = proposal.getExpedition().getTeamId();
        teams.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!members.existsByUser_UserIdAndTeam_TeamId(userId, teamId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        record Observed(com.savanna.backend.domain.ProposalRecipient recipient, long version) {}
        var observed = new java.util.ArrayList<Observed>();
        // 동시 확인 요청의 잠금 순서를 맞추기 위해 같은 배치 안에서 일정한 순서로 잠금
        for (var response : request.responses().stream().sorted(java.util.Comparator.comparing(
                ProposalResponsesReadRequest.Response::proposalRecipientId)).toList()) {
            var recipient = recipients.findForResponse(response.proposalRecipientId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!recipient.getProposal().getProposalId().equals(proposalId)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            if (recipient.isRevoked() || !members.existsByUser_UserIdAndTeam_TeamId(recipient.getUser().getUserId(), teamId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (response.responseVersion() > recipient.getResponseVersion()) throw new BusinessException(ErrorCode.CONFLICT);
            observed.add(new Observed(recipient, response.responseVersion()));
        }
        int acknowledged = 0;
        for (var response : observed) if (response.recipient().markResponseRead(response.version())) acknowledged++;
        return new ProposalResponsesReadResponse(proposalId, acknowledged);
    }
}

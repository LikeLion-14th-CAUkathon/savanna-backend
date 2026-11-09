package com.savanna.backend.service;

import com.savanna.backend.dto.response.*;
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
        if (!proposal.getSender().getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        // 집계와 개인별 상태를 같은 조회 결과로 구성합니다.
        return ProposalRecipientsResponse.from(proposalId,
                recipients.findAllByProposal_ProposalIdOrderByProposalRecipientIdAsc(proposalId));
    }
}

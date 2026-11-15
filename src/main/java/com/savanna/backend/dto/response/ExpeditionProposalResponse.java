package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Proposal;
import com.savanna.backend.domain.ProposalRecipient;
import java.time.LocalDateTime;
import java.util.List;

public record ExpeditionProposalResponse(Long proposalId, Long senderId, String senderName,
                                         String message, LocalDateTime createdAt,
                                         long totalCount, long joinedCount, long declinedCount, long pendingCount,
                                         List<ProposalRecipientsResponse.Recipient> recipients,
                                         @io.swagger.v3.oas.annotations.media.Schema(description="발신자의 현재 선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
                                         Integer senderCharacterId) {
    public static ExpeditionProposalResponse from(Proposal proposal, List<ProposalRecipient> recipients) {
        var summary = ProposalRecipientsResponse.from(proposal.getProposalId(), recipients);
        return new ExpeditionProposalResponse(proposal.getProposalId(), proposal.getSender().getUserId(),
                proposal.getSender().getName(), proposal.getMessage(), proposal.getCreatedAt(),
                summary.totalCount(), summary.joinedCount(), summary.declinedCount(), summary.pendingCount(),
                summary.recipients(),proposal.getSender().getCharacterId());
    }
}

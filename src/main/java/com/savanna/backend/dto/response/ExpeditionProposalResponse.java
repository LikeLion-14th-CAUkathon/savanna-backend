package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Proposal;
import com.savanna.backend.domain.ProposalRecipient;
import java.time.LocalDateTime;
import java.util.List;

public record ExpeditionProposalResponse(Long proposalId, Long senderId, String senderName,
                                         String message, LocalDateTime createdAt,
                                         long totalCount, long joinedCount, long declinedCount, long pendingCount,
                                         List<ProposalRecipientsResponse.Recipient> recipients) {
    public static ExpeditionProposalResponse from(Proposal proposal, List<ProposalRecipient> recipients) {
        var summary = ProposalRecipientsResponse.from(proposal.getProposalId(), recipients);
        return new ExpeditionProposalResponse(proposal.getProposalId(), proposal.getSender().getUserId(),
                proposal.getSender().getName(), proposal.getMessage(), proposal.getCreatedAt(),
                summary.totalCount(), summary.joinedCount(), summary.declinedCount(), summary.pendingCount(),
                summary.recipients());
    }
}

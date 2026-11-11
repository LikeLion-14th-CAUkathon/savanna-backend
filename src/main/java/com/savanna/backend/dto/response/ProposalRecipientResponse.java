package com.savanna.backend.dto.response;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.enums.ProposalRecipientStatus;
import java.time.LocalDateTime;

public record ProposalRecipientResponse(Long proposalRecipientId, Long proposalId, Long expeditionId,
                                        Long teamId, ProposalRecipientStatus status, LocalDateTime respondedAt, long responseVersion) {
    public static ProposalRecipientResponse from(ProposalRecipient recipient) {
        var proposal = recipient.getProposal();
        return new ProposalRecipientResponse(recipient.getProposalRecipientId(), proposal.getProposalId(),
                proposal.getExpedition().getExpeditionId(), proposal.getExpedition().getTeamId(),
                recipient.getStatus(), recipient.getRespondedAt(), recipient.getResponseVersion());
    }
}

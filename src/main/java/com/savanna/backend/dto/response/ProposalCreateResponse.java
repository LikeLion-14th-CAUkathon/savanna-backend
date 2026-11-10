package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Proposal;

public record ProposalCreateResponse(
        Long proposalId,
        Long expeditionId
) {

    public static ProposalCreateResponse from(Proposal proposal) {
        return new ProposalCreateResponse(
                proposal.getProposalId(),
                proposal.getExpedition().getExpeditionId()
        );
    }
}
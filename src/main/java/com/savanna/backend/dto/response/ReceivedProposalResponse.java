package com.savanna.backend.dto.response;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.enums.ProposalRecipientStatus;

import java.time.LocalDateTime;

public record ReceivedProposalResponse(
        Long proposalRecipientId,
        Long proposalId,
        Long expeditionId,
        Long announcementId,
        String announcementTitle,
        String senderName,
        String message,
        ProposalRecipientStatus status,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static ReceivedProposalResponse from(
            ProposalRecipient recipient
    ) {
        var proposal = recipient.getProposal();
        var expedition = proposal.getExpedition();
        var announcement = expedition.getAnnouncement();

        return new ReceivedProposalResponse(
                recipient.getProposalRecipientId(),
                proposal.getProposalId(),
                expedition.getExpeditionId(),
                announcement.getAnnouncementId(),
                announcement.getTitle(),
                proposal.getSender().getName(),
                proposal.getMessage(),
                recipient.getStatus(),
                proposal.getCreatedAt(),
                recipient.getReadAt()
        );
    }
}
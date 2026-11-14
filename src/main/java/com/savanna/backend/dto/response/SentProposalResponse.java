package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Proposal;
import java.time.LocalDateTime;

public record SentProposalResponse(Long proposalId, Long expeditionId, Long teamId,
                                   Long announcementId, String announcementTitle,
                                   String message, LocalDateTime createdAt) {
    public static SentProposalResponse from(Proposal proposal) {
        var expedition = proposal.getExpedition();
        var announcement = expedition.getAnnouncement();
        return new SentProposalResponse(proposal.getProposalId(), expedition.getExpeditionId(),
                expedition.getTeamId(), announcement.getAnnouncementId(), announcement.getTitle(),
                proposal.getMessage(), proposal.getCreatedAt());
    }
}

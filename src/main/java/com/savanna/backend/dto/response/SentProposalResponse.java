package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Proposal;
import java.time.LocalDateTime;

public record SentProposalResponse(Long proposalId, Long expeditionId, Long teamId,
                                   Long announcementId, String announcementTitle,
                                   String message, LocalDateTime createdAt,
                                   @io.swagger.v3.oas.annotations.media.Schema(description="발신자의 현재 선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
                                   Integer senderCharacterId) {
    public static SentProposalResponse from(Proposal proposal) {
        var expedition = proposal.getExpedition();
        var announcement = expedition.getAnnouncement();
        return new SentProposalResponse(proposal.getProposalId(), expedition.getExpeditionId(),
                expedition.getTeamId(), announcement.getAnnouncementId(), announcement.getTitle(),
                proposal.getMessage(), proposal.getCreatedAt(),proposal.getSender().getCharacterId());
    }
}

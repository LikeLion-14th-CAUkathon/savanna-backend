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
        LocalDateTime readAt,
        Long senderId,
        @io.swagger.v3.oas.annotations.media.Schema(description="발신자의 현재 선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
        Integer senderCharacterId
) {

    public static ReceivedProposalResponse from(
            ProposalRecipient recipient
    ) {
        var proposal = recipient.getProposal();
        var expedition = proposal.getExpedition();
        var announcement = expedition.getAnnouncement();
        var sender = proposal.getSender();
        String senderName = sender != null ? sender.getName() : "탈퇴한 팀원";
        Long senderId = sender != null ? sender.getUserId() : null;
        Integer senderCharacterId = sender != null ? sender.getCharacterId() : null;

        return new ReceivedProposalResponse(
                recipient.getProposalRecipientId(),
                proposal.getProposalId(),
                expedition.getExpeditionId(),
                announcement.getAnnouncementId(),
                announcement.getTitle(),
                senderName,
                proposal.getMessage(),
                recipient.getStatus(),
                proposal.getCreatedAt(),
                recipient.getReadAt(),
                senderId,
                senderCharacterId
        );
    }
}
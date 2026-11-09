package com.savanna.backend.dto.response;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.enums.ProposalRecipientStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ProposalRecipientsResponse(Long proposalId, long totalCount, long joinedCount,
                                        long declinedCount, long pendingCount, List<Recipient> recipients) {
    public record Recipient(Long proposalRecipientId, Long userId, String name,
                            ProposalRecipientStatus status, LocalDateTime readAt, LocalDateTime respondedAt) {
        public static Recipient from(ProposalRecipient recipient) {
            return new Recipient(recipient.getProposalRecipientId(), recipient.getUser().getUserId(),
                    recipient.getUser().getName(), recipient.getStatus(), recipient.getReadAt(), recipient.getRespondedAt());
        }
    }

    public static ProposalRecipientsResponse from(Long proposalId, List<ProposalRecipient> records) {
        var recipients = records.stream().map(Recipient::from).toList();
        long joined = recipients.stream().filter(r -> r.status() == ProposalRecipientStatus.JOINED).count();
        long declined = recipients.stream().filter(r -> r.status() == ProposalRecipientStatus.DECLINED).count();
        long pending = recipients.stream().filter(r -> r.status() == ProposalRecipientStatus.PENDING).count();
        return new ProposalRecipientsResponse(proposalId, recipients.size(), joined, declined, pending, recipients);
    }
}

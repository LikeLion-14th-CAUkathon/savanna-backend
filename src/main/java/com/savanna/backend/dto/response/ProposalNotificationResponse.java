package com.savanna.backend.dto.response;

public record ProposalNotificationResponse(boolean hasUnread, long unreadProposalCount, long unreadResponseCount) {}

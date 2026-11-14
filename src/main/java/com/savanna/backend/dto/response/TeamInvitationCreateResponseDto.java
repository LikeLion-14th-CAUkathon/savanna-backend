package com.savanna.backend.dto.response;

import java.time.Instant;

public record TeamInvitationCreateResponseDto(
        Long invitationId,
        Long teamId,
        String token,
        Instant expiresAt
) {}

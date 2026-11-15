package com.savanna.backend.dto.response;

import java.time.Instant;

public record TeamInvitationCreateResponseDto(
        Long invitationId,
        Long teamId,
        String token,
        @io.swagger.v3.oas.annotations.media.Schema(description="영구 링크는 null",nullable=true)
        Instant expiresAt,
        @io.swagger.v3.oas.annotations.media.Schema(description="공유할 프런트 초대 URL. 브라우저 origin으로 다시 조합하지 말고 이 값을 그대로 사용합니다.")
        String inviteUrl,
        com.savanna.backend.enums.TeamInvitationStatus status
) {}

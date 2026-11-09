package com.savanna.backend.controller;

import com.savanna.backend.dto.response.TeamInvitationCreateResponseDto;
import com.savanna.backend.service.TeamInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.savanna.backend.dto.response.TeamInvitationPreviewResponseDto;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService invitationService;

    @PostMapping("/api/v1/teams/{teamId}/invitations")
    public ResponseEntity<?> createInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {

        TeamInvitationCreateResponseDto result =
                invitationService.createInvitation(userId, teamId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "status", 201,
                        "message", "초대 링크 생성 성공",
                        "data", result
                ));
    }

    @GetMapping("/api/v1/invitations/{token}")
    public ResponseEntity<?> previewInvitation(
            @PathVariable String token
    ) {
        TeamInvitationPreviewResponseDto result =
                invitationService.previewInvitation(token);

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "초대 링크 조회 성공",
                        "data", result
                )
        );
    }
}

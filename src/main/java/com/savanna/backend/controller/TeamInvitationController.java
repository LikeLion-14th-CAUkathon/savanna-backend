package com.savanna.backend.controller;

import com.savanna.backend.dto.response.TeamInvitationCreateResponseDto;
import com.savanna.backend.service.TeamInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.savanna.backend.dto.response.TeamInvitationPreviewResponseDto;
import com.savanna.backend.dto.response.TeamInvitationAcceptResponseDto;

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

    @PostMapping("/api/v1/invitations/{token}/accept")
    public ResponseEntity<?> acceptInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token
    ) {
        TeamInvitationAcceptResponseDto result =
                invitationService.acceptInvitation(userId, token);

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "팀 합류 성공",
                        "data", result
                )
        );
    }

    @GetMapping("/api/v1/teams/{teamId}/invitations")
    public ResponseEntity<?> getInvitations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {
        var result = invitationService.getInvitations(
                userId,
                teamId
        );

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "초대 목록 조회 성공",
                        "data", result
                )
        );
    }

    @DeleteMapping(
            "/api/v1/teams/{teamId}/invitations/{invitationId}"
    )
    public ResponseEntity<?> revokeInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId,
            @PathVariable Long invitationId
    ) {
        invitationService.revokeInvitation(
                userId,
                teamId,
                invitationId
        );

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "초대 링크 폐기 성공"
                )
        );
    }
}

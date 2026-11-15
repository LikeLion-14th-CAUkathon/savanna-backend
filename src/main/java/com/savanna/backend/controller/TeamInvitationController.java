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



@RestController
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService invitationService;

    @PostMapping("/api/v1/teams/{teamId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationCreateResponseDto>> createInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {

        TeamInvitationCreateResponseDto result =
                invitationService.createInvitation(userId, teamId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new com.savanna.backend.dto.response.DataResponse<>(201,"초대 링크 생성 성공",result));
    }

    @GetMapping("/api/v1/invitations/{token}")
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationPreviewResponseDto>> previewInvitation(
            @PathVariable String token
    ) {
        TeamInvitationPreviewResponseDto result =
                invitationService.previewInvitation(token);

        return ResponseEntity.ok(
                new com.savanna.backend.dto.response.DataResponse<>(200,"초대 링크 조회 성공",result)
        );
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationAcceptResponseDto>> acceptInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token
    ) {
        TeamInvitationAcceptResponseDto result =
                invitationService.acceptInvitation(userId, token);

        return ResponseEntity.ok(
                new com.savanna.backend.dto.response.DataResponse<>(200,"팀 합류 성공",result)
        );
    }

    @GetMapping("/api/v1/teams/{teamId}/invitations")
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<java.util.List<com.savanna.backend.dto.response.TeamInvitationListResponseDto>>> getInvitations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {
        var result = invitationService.getInvitations(
                userId,
                teamId
        );

        return ResponseEntity.ok(
                new com.savanna.backend.dto.response.DataResponse<>(200,"초대 목록 조회 성공",result)
        );
    }

    @DeleteMapping(
            "/api/v1/teams/{teamId}/invitations/{invitationId}"
    )
    public ResponseEntity<com.savanna.backend.dto.response.StatusMessageResponse> revokeInvitation(
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
                new com.savanna.backend.dto.response.StatusMessageResponse(200,"초대 링크 폐기 성공")
        );
    }
}

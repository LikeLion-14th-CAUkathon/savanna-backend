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

    @io.swagger.v3.oas.annotations.Operation(summary="팀의 현재 초대 링크 조회",description="로그인한 팀원 누구나 동일한 활성 영구 링크를 조회합니다. 생성·갱신하지 않습니다. 링크가 없으면 404이며 팀장이 기존 생성 API를 호출해야 합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="현재 공유 링크"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="로그인 필요",content=@io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="팀원 아님",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.savanna.backend.error.ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="팀 또는 활성 영구 링크 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="서버 설정 변경 등으로 토큰 복원 불가")
    })
    @GetMapping("/api/v1/teams/{teamId}/invitations/current")
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationCreateResponseDto>> currentInvitation(
            @AuthenticationPrincipal Long userId,@PathVariable Long teamId) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new com.savanna.backend.dto.response.DataResponse<>(200,"현재 초대 링크 조회 성공",invitationService.currentInvitation(userId,teamId)));
    }

    @PostMapping("/api/v1/teams/{teamId}/invitations")
    @io.swagger.v3.oas.annotations.Operation(summary="팀 초대 링크 생성 또는 재사용",description="팀장 전용. 기존 활성 영구 링크가 있으면 같은 토큰을 재사용합니다. 영구 링크는 expiresAt=null, status=ACTIVE입니다.")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationCreateResponseDto>> createInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {

        TeamInvitationCreateResponseDto result =
                invitationService.createInvitation(userId, teamId);

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new com.savanna.backend.dto.response.DataResponse<>(201,"초대 링크 생성 성공",result));
    }

    @GetMapping("/api/v1/invitations/{token}")
    @io.swagger.v3.oas.annotations.Operation(summary="초대 링크 미리보기",description="공개 조회. 현재 팀원 members의 name과 characterId(숫자 1~9, 미설정 null)를 함께 반환합니다. 탈퇴자는 제외합니다. 만료된 과거 링크 또는 폐기된 링크는 410입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="유효한 초대 링크 미리보기")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="410",description="EXPIRED 또는 REVOKED 링크",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.savanna.backend.error.ErrorResponse.class)))
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationPreviewResponseDto>> previewInvitation(
            @PathVariable String token
    ) {
        TeamInvitationPreviewResponseDto result =
                invitationService.previewInvitation(token);

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new com.savanna.backend.dto.response.DataResponse<>(200,"초대 링크 조회 성공",result)
        );
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    @io.swagger.v3.oas.annotations.Operation(summary="초대 링크로 팀 가입",description="미소속 계정만 가입합니다. 만료·폐기 링크는 410, 기존 팀 소속은 409입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="팀 가입 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="410",description="EXPIRED 또는 REVOKED 링크",content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.savanna.backend.error.ErrorResponse.class)))
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<TeamInvitationAcceptResponseDto>> acceptInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token
    ) {
        TeamInvitationAcceptResponseDto result =
                invitationService.acceptInvitation(userId, token);

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new com.savanna.backend.dto.response.DataResponse<>(200,"팀 합류 성공",result)
        );
    }

    @GetMapping("/api/v1/teams/{teamId}/invitations")
    @io.swagger.v3.oas.annotations.Operation(summary="팀 초대 목록 조회",description="팀장 전용. ACTIVE/REVOKED/EXPIRED를 반환합니다. EXPIRED는 과거 7일형 링크에만 발생합니다. 활성 영구 링크에만 복원 가능한 token과 inviteUrl을 제공합니다.")
    public ResponseEntity<com.savanna.backend.dto.response.DataResponse<java.util.List<com.savanna.backend.dto.response.TeamInvitationListResponseDto>>> getInvitations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {
        var result = invitationService.getInvitations(
                userId,
                teamId
        );

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
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

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new com.savanna.backend.dto.response.StatusMessageResponse(200,"초대 링크 폐기 성공")
        );
    }
}

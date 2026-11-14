package com.savanna.backend.controller;

import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.dto.response.TeamCreateResponseDto;
import com.savanna.backend.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.savanna.backend.dto.response.TeamListResponseDto;
import com.savanna.backend.dto.response.TeamDetailResponseDto;
import com.savanna.backend.dto.response.DataResponse;
import com.savanna.backend.dto.request.TeamUpdateRequestDto;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final com.savanna.backend.service.TeamPhotoService teamPhotos;

    @io.swagger.v3.oas.annotations.Operation(summary="팀 사진 업로드",description="팀장 전용. JPEG/PNG, 5 MiB 이하. photoUrl은 600초 임시 조회 URL입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="업로드 및 팀 사진 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="누락·손상 이미지 또는 해상도 초과"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="팀장 권한 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="팀 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="5 MiB 초과"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="415",description="JPEG·PNG만 지원"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="저장소 비활성 또는 AWS 오류")
    })
    @PostMapping(value="/{teamId}/photo",consumes="multipart/form-data")
    public ResponseEntity<DataResponse<com.savanna.backend.service.TeamPhotoService.PhotoResponse>> uploadPhoto(
            @AuthenticationPrincipal Long userId, @PathVariable Long teamId,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new DataResponse<>(200,"팀 사진 업로드 성공",teamPhotos.upload(userId,teamId,file)));
    }

    @PostMapping
    public ResponseEntity<DataResponse<TeamCreateResponseDto>> createTeam(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamCreateRequestDto request
    ) {

        TeamCreateResponseDto result =
                teamService.createTeam(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DataResponse<>(201,"팀 생성 성공",result));
    }

    @GetMapping
    public ResponseEntity<DataResponse<List<TeamListResponseDto>>> getMyTeams(
            @AuthenticationPrincipal Long userId
    ) {
        List<TeamListResponseDto> result =
                teamService.getMyTeams(userId);

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new DataResponse<>(200,"내 팀 목록 조회 성공",result)
        );
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<DataResponse<TeamDetailResponseDto>> getTeamDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {
        TeamDetailResponseDto result =
                teamService.getTeamDetail(userId, teamId);

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new DataResponse<>(200,"팀 상세 조회 성공",result)
        );
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<DataResponse<TeamDetailResponseDto>> updateTeam(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamUpdateRequestDto request
    ) {
        TeamDetailResponseDto result =
                teamService.updateTeam(userId, teamId, request);

        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new DataResponse<>(200,"팀 정보 수정 성공",result)
        );
    }

}

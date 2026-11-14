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

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<?> createTeam(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamCreateRequestDto request
    ) {

        TeamCreateResponseDto result =
                teamService.createTeam(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "status", 201,
                        "message", "팀 생성 성공",
                        "data", result
                ));
    }

    @GetMapping
    public ResponseEntity<?> getMyTeams(
            @AuthenticationPrincipal Long userId
    ) {
        List<TeamListResponseDto> result =
                teamService.getMyTeams(userId);

        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "message", "내 팀 목록 조회 성공",
                        "data", result
                )
        );
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<DataResponse<TeamDetailResponseDto>> getTeamDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long teamId
    ) {
        TeamDetailResponseDto result =
                teamService.getTeamDetail(userId, teamId);

        return ResponseEntity.ok(
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

        return ResponseEntity.ok(
                new DataResponse<>(200,"팀 정보 수정 성공",result)
        );
    }

}

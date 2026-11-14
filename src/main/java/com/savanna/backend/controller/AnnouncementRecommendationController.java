package com.savanna.backend.controller;

import com.savanna.backend.dto.response.AnnouncementRecommendationResponse;
import com.savanna.backend.service.AnnouncementRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/recommendations")
public class AnnouncementRecommendationController {
    private final AnnouncementRecommendationService service;
    @Operation(summary = "우리 팀 AI 추천 목록 조회", description = "저장된 추천을 점수순으로 반환합니다. 조회 시 AI를 호출하지 않습니다.")
    @GetMapping
    public List<AnnouncementRecommendationResponse> list(@PathVariable("teamId") Long teamId, Principal principal) {
        return service.list(teamId, principal);
    }

    @Operation(summary = "우리 팀 AI 추천 재생성", description = "현재 팀 정보로 후보를 선정해 추천을 교체합니다. 실제 AI 미연결 시 503입니다.")
    @PostMapping("/refresh")
    public List<AnnouncementRecommendationResponse> refresh(@PathVariable("teamId") Long teamId, Principal principal) {
        return service.refresh(teamId, principal);
    }
}

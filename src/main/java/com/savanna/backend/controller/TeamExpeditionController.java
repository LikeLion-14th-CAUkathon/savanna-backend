package com.savanna.backend.controller;

import com.savanna.backend.dto.response.TeamExpeditionResponse;
import com.savanna.backend.service.ExpeditionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/expeditions")
public class TeamExpeditionController {
    private final ExpeditionService expeditionService;

    @Operation(summary = "오아시스 원정 목록 조회",
            description = "공고 마감순으로 원정을 반환합니다. 합류 인원은 JOINED 응답의 중복 없는 사용자 수입니다. 마감·제출 완료 원정도 포함합니다.")
    @GetMapping
    public List<TeamExpeditionResponse> getExpeditions(@PathVariable("teamId") Long teamId, Principal principal) {
        return expeditionService.getTeamExpeditions(teamId, principal);
    }
}

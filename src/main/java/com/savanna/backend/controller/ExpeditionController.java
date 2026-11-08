package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ExpeditionScheduleRequest;
import com.savanna.backend.dto.request.ExpeditionStageRequest;
import com.savanna.backend.dto.response.ExpeditionResponse;
import com.savanna.backend.dto.response.ExpeditionStageHistoryResponse;
import com.savanna.backend.service.ExpeditionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/expeditions")
public class ExpeditionController {
    private final ExpeditionService expeditionService;

    @Operation(summary = "준비 정보 조회")
    @GetMapping("/{expeditionId}")
    public ExpeditionResponse getExpedition(@PathVariable("expeditionId") Long id, Principal principal) {
        return expeditionService.getExpedition(id, principal);
    }

    @Operation(summary = "진행 단계 변경")
    @PatchMapping("/{expeditionId}/stage")
    public ExpeditionResponse changeStage(@PathVariable("expeditionId") Long id,
            @Valid @RequestBody ExpeditionStageRequest request, Principal principal) {
        return expeditionService.changeStage(id, request, principal);
    }

    @Operation(summary = "준비 일정 수정", description = "생략 또는 null 필드는 기존 값을 유지합니다.")
    @PatchMapping("/{expeditionId}/schedule")
    public ExpeditionResponse updateSchedule(@PathVariable("expeditionId") Long id,
            @RequestBody ExpeditionScheduleRequest request, Principal principal) {
        return expeditionService.updateSchedule(id, request, principal);
    }

    @Operation(summary = "단계 변경 이력 조회")
    @GetMapping("/{expeditionId}/stage-histories")
    public Page<ExpeditionStageHistoryResponse> getStageHistories(@PathVariable("expeditionId") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size, Principal principal) {
        return expeditionService.getStageHistories(id, page, size, principal);
    }
}

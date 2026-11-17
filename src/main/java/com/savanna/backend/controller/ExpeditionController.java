package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ExpeditionScheduleRequest;
import com.savanna.backend.dto.request.ExpeditionStageRequest;
import com.savanna.backend.dto.response.ExpeditionProposalResponse;
import com.savanna.backend.dto.response.ExpeditionResponse;
import com.savanna.backend.dto.response.ExpeditionStageHistoryResponse;
import com.savanna.backend.service.ExpeditionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/expeditions")
public class ExpeditionController {
    private final ExpeditionService expeditionService;

    @Operation(summary = "원정의 제안과 응답 현황 조회", description = "해당 팀원이 원정의 제안과 수신자별 응답·집계를 조회합니다.")
    @GetMapping("/{expeditionId}/proposals")
    public List<ExpeditionProposalResponse> getProposals(
            @PathVariable("expeditionId") Long id, Principal principal) {
        return expeditionService.getProposals(id, principal);
    }

    @Operation(summary = "준비 정보 조회", description = "joinedCount는 현재 팀 소속 JOINED 수신자의 고유 인원입니다(발신자 제외). memberCountNotice는 확인된 최소 인원 미달 시 '현재 N명이 합류했어요. 최소 M명이 필요해요.', 최대 인원 초과 시 '현재 N명이 합류했어요. 최대 M명까지 참가할 수 있어요.'를 반환합니다. 범위 내이거나 해당 제한이 미확인이면 null입니다.")
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

    @Operation(summary = "오아시스 원정 삭제", description = "원정과 연결된 제안, 수신자 응답, 일정, 체크리스트, 단계 변경 이력을 함께 삭제합니다.")
    @DeleteMapping("/{expeditionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpedition(@PathVariable("expeditionId") Long id, Principal principal) {
        expeditionService.deleteExpedition(id, principal);
    }
}
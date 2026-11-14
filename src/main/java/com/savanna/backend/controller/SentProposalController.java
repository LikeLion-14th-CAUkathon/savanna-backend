package com.savanna.backend.controller;

import com.savanna.backend.dto.response.*;
import com.savanna.backend.dto.request.ProposalResponsesReadRequest;
import jakarta.validation.Valid;
import com.savanna.backend.service.SentProposalService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/proposals")
public class SentProposalController {
    private final SentProposalService service;

    @Operation(summary = "내가 보낸 제안 목록 조회", description = "본인이 발신자인 제안을 최신순으로 반환합니다.")
    @GetMapping("/sent")
    public List<SentProposalResponse> sent(@AuthenticationPrincipal Long userId) {
        return service.sent(userId);
    }

    @Operation(summary = "보낸 제안 응답 현황 조회", description = "발신자 본인에게 합류·쉬어가기·미응답 집계와 수신자별 응답을 반환합니다.")
    @GetMapping("/{proposalId}/recipients")
    public ProposalRecipientsResponse recipients(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalId") Long proposalId) {
        return service.recipients(userId, proposalId);
    }

    @Operation(summary = "보낸 제안의 응답 확인 처리", description = "발신자만 화면에서 실제 확인한 수신 기록 ID와 responseVersion을 전달합니다.")
    @PatchMapping("/{proposalId}/recipients/read")
    public ProposalResponsesReadResponse markResponsesRead(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalId") Long proposalId, @Valid @RequestBody ProposalResponsesReadRequest request) {
        return service.markResponsesRead(userId, proposalId, request);
    }
}

package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ProposalResponseRequest;
import com.savanna.backend.dto.response.ProposalRecipientResponse;
import com.savanna.backend.dto.response.ReceivedProposalResponse;
import com.savanna.backend.dto.response.ProposalPendingCountResponse;
import com.savanna.backend.service.ProposalRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/proposal-recipients")
public class ProposalRecipientController {
    private final ProposalRecipientService service;

    @Operation(summary = "받은 참여 제안 목록 조회", description = "본인이 받은 제안을 최신순으로 조회합니다. 성공적으로 표시한 제안은 별도 읽음 API로 확인합니다.")
    @GetMapping("/received")
    public java.util.List<ReceivedProposalResponse> received(@AuthenticationPrincipal Long userId) {
        return service.received(userId);
    }

    @Operation(summary = "미응답 참여 제안 개수 조회", description = "PENDING 개수이며 미확인 알림 점과는 별개입니다.")
    @GetMapping("/pending-count")
    public ProposalPendingCountResponse pendingCount(@AuthenticationPrincipal Long userId) {
        return service.pendingCount(userId);
    }

    @Operation(summary = "받은 참여 제안 읽음 처리", description = "본인의 수신 기록만 처리하며 최초 읽음 시간을 유지합니다.")
    @PatchMapping("/{proposalRecipientId}/read")
    public ReceivedProposalResponse markAsRead(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalRecipientId") Long id) {
        return service.markAsRead(userId, id);
    }

    @Operation(summary = "제안 응답", description = "JOINED와 DECLINED 간 변경을 허용합니다. 동일 응답 재요청은 시간을 변경하지 않습니다.")
    @PatchMapping("/{proposalRecipientId}/response")
    public ProposalRecipientResponse respond(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalRecipientId") Long id, @Valid @RequestBody ProposalResponseRequest request) {
        return service.respond(userId, id, request.status());
    }
}

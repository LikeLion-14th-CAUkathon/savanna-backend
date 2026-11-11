package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ProposalResponseRequest;
import com.savanna.backend.dto.response.ProposalRecipientResponse;
import com.savanna.backend.service.ProposalRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.savanna.backend.dto.response.ReceivedProposalResponse;
import com.savanna.backend.dto.response.ProposalPendingCountResponse;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/proposal-recipients")
public class ProposalRecipientController {
    private final ProposalRecipientService service;

    @Operation(summary = "제안 응답", description = "JOINED(합류) 또는 DECLINED(쉬어가기). 응답 완료된 제안은 409입니다.")
    @PatchMapping("/{proposalRecipientId}/response")
    public ProposalRecipientResponse respond(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalRecipientId") Long id, @Valid @RequestBody ProposalResponseRequest request) {
        return service.respond(userId, id, request.status());
    }

    @Operation(
            summary = "받은 참여 제안 목록 조회",
            description = "로그인 사용자가 받은 참여 제안을 최신순으로 조회합니다."
    )
    @GetMapping("/received")
    public List<ReceivedProposalResponse> received(
            @AuthenticationPrincipal Long userId
    ) {
        return service.received(userId);
    }

    @Operation(
            summary = "미응답 참여 제안 개수 조회",
            description = "아직 수락 또는 거절하지 않은 참여 제안 개수를 반환합니다."
    )
    @GetMapping("/pending-count")
    public ProposalPendingCountResponse pendingCount(
            @AuthenticationPrincipal Long userId
    ) {
        return service.pendingCount(userId);
    }

    @Operation(
            summary = "받은 참여 제안 읽음 처리",
            description = "본인이 받은 참여 제안을 읽음 처리합니다. 이미 읽은 제안은 기존 읽음 시간을 유지합니다."
    )
    @PatchMapping("/{proposalRecipientId}/read")
    public ReceivedProposalResponse markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("proposalRecipientId") Long recipientId
    ) {
        return service.markAsRead(userId, recipientId);
    }

}

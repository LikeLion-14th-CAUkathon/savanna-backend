package com.savanna.backend.controller;

import com.savanna.backend.dto.response.*;
import com.savanna.backend.service.ProposalRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/proposals/received")
public class ReceivedProposalController {
    private final ProposalRecipientService service;

    @Operation(summary = "받은 제안 목록 조회", description = "내가 수신자인 제안을 최신순으로 반환합니다. 읽음 상태는 변경하지 않습니다.")
    @GetMapping
    public List<ReceivedProposalResponse> received(@AuthenticationPrincipal Long userId) {
        return service.received(userId);
    }

    @Operation(summary = "받은 제안 미응답 개수 조회")
    @GetMapping("/pending-count")
    public ProposalPendingCountResponse pendingCount(@AuthenticationPrincipal Long userId) {
        return service.pendingCount(userId);
    }
}

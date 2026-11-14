package com.savanna.backend.controller;

import com.savanna.backend.dto.response.ProposalNotificationResponse;
import com.savanna.backend.service.ProposalRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/proposals/notifications")
public class ProposalNotificationController {
    private final ProposalRecipientService service;

    @Operation(summary = "팀 소식 알림 점 조회", description = "안 읽은 새 제안 또는 발신자가 확인하지 않은 새 응답이 있으면 hasUnread=true입니다. 미응답 개수와 별개입니다.")
    @GetMapping
    public ProposalNotificationResponse notifications(@AuthenticationPrincipal Long userId) {
        return service.notifications(userId);
    }
}

package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ProposalResponseRequest;
import com.savanna.backend.dto.response.ProposalRecipientResponse;
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

    @Operation(summary = "제안 응답", description = "JOINED(합류) 또는 DECLINED(쉬어가기). 응답 완료된 제안은 409입니다.")
    @PatchMapping("/{proposalRecipientId}/response")
    public ProposalRecipientResponse respond(@AuthenticationPrincipal Long userId,
            @PathVariable("proposalRecipientId") Long id, @Valid @RequestBody ProposalResponseRequest request) {
        return service.respond(userId, id, request.status());
    }
}

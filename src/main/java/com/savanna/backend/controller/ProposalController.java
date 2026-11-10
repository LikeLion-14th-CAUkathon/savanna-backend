package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ProposalCreateRequest;
import com.savanna.backend.dto.response.ProposalCreateResponse;
import com.savanna.backend.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/announcements/{announcementId}/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    @Operation(
            summary = "공고 참여 제안 전송",
            description = "원정과 제안, 수신자 기록을 함께 생성합니다. "
                    + "같은 팀이 같은 공고에 이미 제안했다면 중복 요청으로 거절합니다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalCreateResponse createProposal(
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody ProposalCreateRequest request,
            Principal principal
    ) {
        return proposalService.createProposal(
                announcementId,
                request,
                principal
        );
    }
}
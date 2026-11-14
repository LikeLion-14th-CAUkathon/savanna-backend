package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ProposalCreateRequest;
import com.savanna.backend.dto.response.ProposalCreateResponse;
import com.savanna.backend.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import com.savanna.backend.error.ErrorResponse;
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
                    + " 팀원이나 메시지를 바꿔도 같은 팀·공고 조합이면 409이며 새 원정·제안·수신 기록을 만들지 않습니다."
                    + " 다른 팀은 같은 공고에 별도로 제안할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode="201",description="최초 제안 전송 성공",content=@Content(mediaType="application/json",schema=@Schema(implementation=ProposalCreateResponse.class))),
            @ApiResponse(responseCode="400",description="잘못된 요청 또는 접수 전·마감된 공고",content=@Content(mediaType="application/json",schema=@Schema(implementation=ErrorResponse.class))),
            @ApiResponse(responseCode="401",description="미인증",content=@Content),
            @ApiResponse(responseCode="403",description="해당 팀 소속이 아님",content=@Content(mediaType="application/json",schema=@Schema(implementation=ErrorResponse.class))),
            @ApiResponse(responseCode="404",description="공고 또는 팀 없음, 비공개·제외 공고",content=@Content(mediaType="application/json",schema=@Schema(implementation=ErrorResponse.class))),
            @ApiResponse(responseCode="409",description="같은 팀이 같은 공고에 이미 제안했거나 수신자 정보가 변경됨",content=@Content(
                    mediaType="application/json",schema=@Schema(implementation=ErrorResponse.class),examples=@ExampleObject(name="중복 제안",value="{\"code\":\"CONFLICT\",\"message\":\"현재 상태에서 요청을 처리할 수 없습니다.\",\"errors\":[]}")))
    })
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

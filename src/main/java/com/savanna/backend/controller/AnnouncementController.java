package com.savanna.backend.controller;

import com.savanna.backend.dto.request.AnnouncementCreateRequest;
import com.savanna.backend.dto.response.AnnouncementResponse;
import com.savanna.backend.dto.response.AnnouncementDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ReceptionStatus;
import com.savanna.backend.service.AnnouncementService;
import com.savanna.backend.service.AnnouncementEligibilityService;
import com.savanna.backend.dto.response.AnnouncementEligibilityResponse;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AnnouncementEligibilityService announcementEligibilityService;

    @Operation(summary = "공고 참가 조건 확인", description = "결과는 저장하지 않습니다. 팀 연동 전이거나 정보가 불충분하면 TEAM_INFO_REQUIRED를 반환합니다.")
    @GetMapping("/{announcementId}/eligibility")
    public AnnouncementEligibilityResponse checkEligibility(
            @PathVariable("announcementId") Long announcementId,
            @RequestParam(name = "teamId", required = false) Long teamId,
            Principal principal
    ) {
        return announcementEligibilityService.check(announcementId, teamId, principal);
    }

    @Operation(summary = "공고 상세 조회", description = "공개 공고의 상세 정보를 조회합니다. 누락된 상세값은 null이며 프런트에서 해당 행을 숨깁니다. 지원 방법·제출물 섹션에 표시할 정보가 모두 없으면 섹션을 숨기고 공고 원문 보기 링크는 유지합니다. 인원 null은 미확인이며 0명·무제한을 의미하지 않습니다. 제외·분류 보류 공고는 404입니다. deadline은 마감 날짜만 제공하며 실제 마감 시각을 제공하지 않습니다.")
    @GetMapping("/{announcementId}")
    public AnnouncementDetailResponse getAnnouncement(
            @PathVariable("announcementId") Long announcementId
    ) {
        return announcementService.getAnnouncement(announcementId);
    }

    @Operation(summary = "공고 목록 조회",description = "일반 공고 목록을 조회합니다.")
    @GetMapping
    public Page<AnnouncementResponse> getAnnouncements(
            @RequestParam(name = "keyword", required = false)
            String keyword,

            @RequestParam(name = "field", required = false)
            AnnouncementField field,

            @RequestParam(name = "status", required = false)
            ReceptionStatus status,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page는 0 이상, size는 1~100이어야 합니다."
            );
        }

        return announcementService.getAnnouncements(
                keyword,
                field,
                status,
                page,
                size
        );
    }
    @Operation(summary = "공고 생성 조회",description = "공고 목록 조회 테스트 목적입니다")
    @PostMapping
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody AnnouncementCreateRequest request
    ) {
        AnnouncementResponse response =
                announcementService.createAnnouncement(request);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/announcements/"
                                + response.announcementId()
                ))
                .body(response);
    }
}

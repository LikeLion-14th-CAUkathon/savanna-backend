package com.savanna.backend.controller;

import com.savanna.backend.dto.response.AnnouncementResponse;
import com.savanna.backend.dto.response.AnnouncementDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ReceptionStatus;
import com.savanna.backend.service.AnnouncementService;
import com.savanna.backend.service.AnnouncementEligibilityService;
import com.savanna.backend.dto.response.AnnouncementEligibilityResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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

    @Operation(summary = "공고 상세 조회", description = "공개 공고의 상세 정보를 조회합니다. 마감된 공개 공고도 조회할 수 있습니다.")
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
}
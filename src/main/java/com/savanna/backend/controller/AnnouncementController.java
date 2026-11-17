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

    @Operation(summary = "공고 참가 조건 확인", description = "결과는 저장하지 않습니다. 온라인 공고는 지역 검사를 생략합니다. 필수 참석 지역(개최 지역 또는 오프라인 장소 안내)이 있는 경우에만 모든 팀원의 참석 가능 지역과 비교하여 충족 시 SATISFIED, 불일치·미설정·장소 불명확 시 TEAM_INFO_REQUIRED를 반환합니다. 외부 API 참가 가능 지역이나 의미가 불명확한 CSV required_region만으로 참석 지역을 추정하지 않습니다. 재학 상태 등은 별도로 검사합니다. 인원은 이 API 및 추천에서 판정하지 않으며 D02 원정 상세에서 합류 인원의 최소 인원 미달·최대 인원 초과를 안내합니다.")
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

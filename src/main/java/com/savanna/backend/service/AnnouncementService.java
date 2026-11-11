package com.savanna.backend.service;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.dto.request.AnnouncementCreateRequest;
import com.savanna.backend.dto.response.AnnouncementResponse;
import com.savanna.backend.dto.response.AnnouncementDetailResponse;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ReceptionStatus;
import com.savanna.backend.repository.AnnouncementRepository;
import com.savanna.backend.repository.AnnouncementSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementDetailResponse getAnnouncement(Long announcementId) {
        if (announcementId == null || announcementId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var announcement = announcementRepository.findByAnnouncementIdAndVisibilityTrue(announcementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return AnnouncementDetailResponse.from(announcement);
    }
    public Page<AnnouncementResponse> getAnnouncements(
            String keyword,
            AnnouncementField field,
            ReceptionStatus status,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지 크기는 1 이상 100 이하여야 합니다."
            );
        }

        // 접수 시작은 한국 시각, 마감은 한국 날짜 기준으로 비교합니다.
        LocalDateTime now = LocalDateTime.now(
                ZoneId.of("Asia/Seoul")
        );

        // 마감 임박순, 마감이 같으면 공고 ID순
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("deadline"),
                        Sort.Order.asc("announcementId")
                )
        );

        // 검색어·분야·접수 상태 조건 생성
        var specification = AnnouncementSpecification.search(
                keyword,
                field,
                status,
                now
        );

        // DB 조회 후 엔티티를 응답 DTO로 변환
        return announcementRepository
                .findAll(specification, pageRequest)
                .map(AnnouncementResponse::from);
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(
            AnnouncementCreateRequest request
    ) {
        Announcement announcement = Announcement.builder()
                .title(request.title().trim())
                .applicationStartsAt(request.applicationStartsAt())
                .deadline(request.deadline())
                .field(request.field())
                .host(request.host().trim())
                .minMembers(request.minMembers())
                .maxMembers(request.maxMembers())
                .evaluationCriteria(request.evaluationCriteria())
                .enrollmentRequirement(request.enrollmentRequirement())
                .region(request.region())
                .theme(request.theme())
                .applicationMethod(request.applicationMethod())
                .submissionRequirements(request.submissionRequirements())
                .sourceUrl(request.sourceUrl())
                .applyUrl(request.applyUrl())
                .build();

        return AnnouncementResponse.from(
                announcementRepository.save(announcement)
        );
    }
}

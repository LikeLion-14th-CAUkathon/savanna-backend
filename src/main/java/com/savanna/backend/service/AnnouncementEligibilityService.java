package com.savanna.backend.service;

import com.savanna.backend.dto.response.AnnouncementEligibilityResponse;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import com.savanna.backend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementEligibilityService {
    private final AnnouncementRepository announcementRepository;
    private final ObjectProvider<EligibilityTeamReader> teamReader;
    private final AnnouncementEligibilityEvaluator evaluator;

    public AnnouncementEligibilityResponse check(Long announcementId, Long teamId, Principal requester) {
        if (announcementId == null || announcementId <= 0 || (teamId != null && teamId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var announcement = announcementRepository.findByAnnouncementIdAndVisibilityTrue(announcementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        EligibilityTeamReader reader = teamReader.getIfAvailable();
        // 인증 사용자를 임의의 userId로 대체하거나 teamId만으로 타 팀 정보를 조회하지 않습니다.
        var team = reader == null || requester == null ? null : reader.read(requester, teamId).orElse(null);
        return evaluator.evaluate(announcement, teamId, team);
    }
}
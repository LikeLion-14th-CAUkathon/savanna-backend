package com.savanna.backend.dto.response;

import com.savanna.backend.enums.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnnouncementRecommendationResponse(Long announcementId, String title, String host,
        AnnouncementField field, LocalDate deadline, ReceptionStatus status, int score, String reason,
        AnnouncementEligibilityResponse eligibility, LocalDateTime generatedAt,
        RecommendationSource source, boolean stale) {}

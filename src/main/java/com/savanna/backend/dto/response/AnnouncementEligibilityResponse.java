package com.savanna.backend.dto.response;

import com.savanna.backend.enums.EligibilityStatus;
import java.util.List;

public record AnnouncementEligibilityResponse(
        Long announcementId, Long teamId, EligibilityStatus status, List<Condition> conditions
) {
    public record Condition(String code, EligibilityStatus status, String message) {}
}
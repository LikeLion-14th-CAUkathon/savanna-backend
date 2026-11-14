package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Expedition;
import com.savanna.backend.enums.ExpeditionStage;
import java.time.LocalDate;

public record TeamExpeditionResponse(
        Long expeditionId, Long announcementId, String title, LocalDate deadline,
        ExpeditionStage stage, long joinedCount
) {
    public static TeamExpeditionResponse from(Expedition e, long joinedCount) {
        return new TeamExpeditionResponse(e.getExpeditionId(), e.getAnnouncement().getAnnouncementId(),
                e.getAnnouncement().getTitle(), e.getAnnouncement().getDeadline(), e.getStage(), joinedCount);
    }
}

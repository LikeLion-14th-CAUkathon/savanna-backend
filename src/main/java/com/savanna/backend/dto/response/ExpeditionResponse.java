package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Expedition;
import com.savanna.backend.enums.ExpeditionStage;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpeditionResponse(
        Long expeditionId, Long teamId, AnnouncementResponse announcement,
        ExpeditionStage stage, LocalDate kickoffDate, LocalDate draftDate,
        LocalDate reviewDate, LocalDate submitTargetDate,
        boolean submissionCompleted, LocalDateTime submissionCompletedAt
) {
    public static ExpeditionResponse from(Expedition e) {
        return new ExpeditionResponse(e.getExpeditionId(), e.getTeamId(),
                AnnouncementResponse.from(e.getAnnouncement()), e.getStage(),
                e.getKickoffDate(), e.getDraftDate(), e.getReviewDate(), e.getSubmitTargetDate(),
                e.isSubmissionCompleted(), e.getSubmissionCompletedAt());
    }
}

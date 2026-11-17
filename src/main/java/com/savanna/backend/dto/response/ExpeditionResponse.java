package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Expedition;
import com.savanna.backend.enums.ExpeditionStage;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpeditionResponse(
        Long expeditionId, Long teamId, AnnouncementResponse announcement,
        ExpeditionStage stage, LocalDate kickoffDate, LocalDate draftDate,
        LocalDate reviewDate, LocalDate submitTargetDate,
        boolean submissionCompleted, LocalDateTime submissionCompletedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "현재 팀 소속 JOINED 수신자의 고유 인원. 발신자·미응답·거절·탈퇴자 제외")
        long joinedCount,
        @io.swagger.v3.oas.annotations.media.Schema(description = "합류 인원이 확인된 최소 인원 미만이거나 최대 인원 초과일 때 현재 인원과 제한을 안내. 범위 내 또는 해당 제한 미확인은 null", nullable = true)
        String memberCountNotice
) {
    public static ExpeditionResponse from(Expedition e) {
        return from(e, 0);
    }

    public static ExpeditionResponse from(Expedition e, long joinedCount) {
        Integer min = e.getAnnouncement().getMinMembers();
        Integer max = e.getAnnouncement().getMaxMembers();
        String notice = null;
        if (max != null && max > 0 && joinedCount > max) {
            notice = "현재 " + joinedCount + "명이 합류했어요. 최대 " + max + "명까지 참가할 수 있어요.";
        } else if (min != null && min > 0 && joinedCount < min) {
            notice = "현재 " + joinedCount + "명이 합류했어요. 최소 " + min + "명이 필요해요.";
        }
        return new ExpeditionResponse(e.getExpeditionId(), e.getTeamId(),
                AnnouncementResponse.from(e.getAnnouncement()), e.getStage(),
                e.getKickoffDate(), e.getDraftDate(), e.getReviewDate(), e.getSubmitTargetDate(),
                e.isSubmissionCompleted(), e.getSubmissionCompletedAt(), joinedCount, notice);
    }
}

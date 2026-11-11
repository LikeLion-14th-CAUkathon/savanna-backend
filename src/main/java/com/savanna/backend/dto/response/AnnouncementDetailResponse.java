package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ApplicationMethod;
import com.savanna.backend.enums.ReceptionStatus;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;

public record AnnouncementDetailResponse(
        Long announcementId,
        String title,
        String host,
        AnnouncementField field,
        LocalDateTime applicationStartsAt,
        LocalDate deadline,
        ReceptionStatus receptionStatus,
        String enrollmentRequirement,
        Integer minMembers,
        Integer maxMembers,
        String region,
        String theme,
        String evaluationCriteria,
        ApplicationMethod applicationMethod,
        String submissionRequirements,
        String sourceUrl,
        String applyUrl,
        String participationRegion,
        String posterUrl,
        String applicationPeriodRaw,
        String applicationInstructions, String formNote, String lengthNote, String teamType,
        String csvRequiredRegionRaw, String venueNote, String detailStatus, String dateSource
) {
    public static AnnouncementDetailResponse from(Announcement announcement) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        ReceptionStatus status;
        if (announcement.getDeadline().isBefore(now.toLocalDate())) {
            status = ReceptionStatus.CLOSED;
        } else if (announcement.getApplicationStartsAt() != null
                && announcement.getApplicationStartsAt().isAfter(now)) {
            status = ReceptionStatus.UPCOMING;
        } else {
            status = ReceptionStatus.OPEN;
        }
        return new AnnouncementDetailResponse(
                announcement.getAnnouncementId(), announcement.getTitle(),
                announcement.getHost(), announcement.getField(),
                announcement.getApplicationStartsAt(), announcement.getDeadline(), status,
                announcement.getEnrollmentRequirement(), announcement.getMinMembers(),
                announcement.getMaxMembers(), announcement.getRegion(), announcement.getTheme(),
                announcement.getEvaluationCriteria(), announcement.getApplicationMethod(),
                announcement.getSubmissionRequirements(),
                announcement.getSourceUrl(), announcement.getApplyUrl(),
                announcement.getParticipationRegion(), announcement.getPosterUrl(), announcement.getApplicationPeriodRaw(),
                announcement.getApplicationInstructions(), announcement.getFormNote(), announcement.getLengthNote(), announcement.getTeamType(),
                announcement.getCsvRequiredRegionRaw(), announcement.getVenueNote(), announcement.getDetailStatus(), announcement.getDateSource()
        );
    }
}

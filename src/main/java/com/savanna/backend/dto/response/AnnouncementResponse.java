package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.AnnouncementField;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record AnnouncementResponse(
        Long announcementId,
        String title,
        String host,
        AnnouncementField field,
        LocalDateTime applicationStartsAt,
        LocalDate deadline
) {

    public static AnnouncementResponse from(
            Announcement announcement
    ) {
        return new AnnouncementResponse(
                announcement.getAnnouncementId(),
                announcement.getTitle(),
                announcement.getHost(),
                announcement.getField(),
                announcement.getApplicationStartsAt(),
                announcement.getDeadline()
        );
    }
}

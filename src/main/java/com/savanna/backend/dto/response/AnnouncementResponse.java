package com.savanna.backend.dto.response;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.AnnouncementField;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long announcementId,
        String title,
        String host,
        AnnouncementField field,
        LocalDateTime applicationStartsAt,
        LocalDateTime deadline
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
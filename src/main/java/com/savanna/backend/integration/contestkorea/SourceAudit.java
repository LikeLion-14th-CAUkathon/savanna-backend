package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.domain.*;
import com.savanna.backend.repository.AnnouncementSourceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service @RequiredArgsConstructor
public class SourceAudit {
    private final AnnouncementSourceEventRepository events;
    public void record(Announcement announcement, String externalId, String source, String kind, Object details) {
        events.save(new AnnouncementSourceEvent(announcement == null ? null : announcement.getAnnouncementId(), externalId,
                source, kind, JsonMapper.builder().build().writeValueAsString(details)));
    }
}

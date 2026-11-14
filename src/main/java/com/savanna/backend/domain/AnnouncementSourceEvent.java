package com.savanna.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "announcement_source_events")
public class AnnouncementSourceEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long announcementId;
    @Column(length = 100) private String externalId;
    @Column(length = 50) private String source;
    @Column(length = 80) private String eventType;
    @Column(columnDefinition = "TEXT") private String details;
    private LocalDateTime recordedAt;
    public AnnouncementSourceEvent(Long announcementId, String externalId, String source, String eventType, String details) {
        this.announcementId=announcementId; this.externalId=externalId; this.source=source;
        this.eventType=eventType; this.details=details; this.recordedAt=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}

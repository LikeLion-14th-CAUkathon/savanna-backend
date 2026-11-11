package com.savanna.backend.domain;

import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ApplicationMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "announcements", uniqueConstraints = @UniqueConstraint(
        name = "uk_announcement_external", columnNames = {"source_provider", "external_id"}))
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Long announcementId;

    // 공고 제목
    @Column(nullable = false, length = 300)
    private String title;

    // 접수 시작 시각
    private LocalDateTime applicationStartsAt;

    // 접수 마감일 (한국 날짜 기준, 당일까지 접수 가능)
    @Column(nullable = false)
    private LocalDate deadline;

    // 공고 분야
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AnnouncementField field;

    // 주최기관
    private String host;

    // 최소·최대 참가 인원 (미확인은 null)
    private Integer minMembers;

    private Integer maxMembers;

    // 심사기준
    @Column(columnDefinition = "TEXT")
    private String evaluationCriteria;

    // 참가 대상·재학 조건 원문
    @Column(columnDefinition = "TEXT")
    private String enrollmentRequirement;

    // 개최·현장 참석 지역
    private String region;

    // 공모 주제
    @Column(columnDefinition = "TEXT")
    private String theme;

    // 접수 방법
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ApplicationMethod applicationMethod;

    // 제출물
    @Column(columnDefinition = "TEXT")
    private String submissionRequirements;

    // 공고 원문 링크
    @Column(length = 2000)
    private String sourceUrl;

    // 지원 사이트 링크
    @Column(length = 2000)
    private String applyUrl;

    @Column(name = "source_provider", length = 40)
    private String sourceProvider;
    @Column(name = "external_id", length = 100)
    private String externalId;
    private String externalCategory;
    private String applicationPeriodRaw;
    // 외부 API의 참가 가능 지역. 기존 개최 지역(region)과 구분합니다.
    private String participationRegion;
    @Column(length = 2000)
    private String posterUrl;
    private LocalDateTime lastSyncedAt;

    // 공개 여부
    @Column(nullable = false)
    private boolean visibility = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Announcement(
            String title,
            LocalDateTime applicationStartsAt,
            LocalDate deadline,
            AnnouncementField field,
            String host,
            Integer minMembers,
            Integer maxMembers,
            String evaluationCriteria,
            String enrollmentRequirement,
            String region,
            String theme,
            ApplicationMethod applicationMethod,
            String submissionRequirements,
            String sourceUrl,
            String applyUrl
    ) {
        this.title = title;
        this.applicationStartsAt = applicationStartsAt;
        this.deadline = deadline;
        this.field = field;
        this.host = host;
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
        this.evaluationCriteria = evaluationCriteria;
        this.enrollmentRequirement = enrollmentRequirement;
        this.region = region;
        this.theme = theme;
        this.applicationMethod = applicationMethod;
        this.submissionRequirements = submissionRequirements;
        this.sourceUrl = sourceUrl;
        this.applyUrl = applyUrl;
    }

    public void changeVisibility(boolean visibility) {
        this.visibility = visibility;
    }

    public void syncContestKorea(String externalId, String category, String title, String host,
            AnnouncementField field, LocalDate start, LocalDate deadline, String target,
            String participationRegion, String periodRaw, String sourceUrl, String posterUrl) {
        this.sourceProvider = "CONTEST_KOREA";
        this.externalId = externalId;
        this.externalCategory = category;
        this.title = title;
        this.host = host;
        this.field = field;
        // API에 시작 시각이 없어 날짜의 시작을 내부 기준으로 사용합니다.
        this.applicationStartsAt = start.atStartOfDay();
        this.deadline = deadline;
        this.enrollmentRequirement = target;
        this.participationRegion = participationRegion;
        this.applicationPeriodRaw = periodRaw;
        this.sourceUrl = sourceUrl;
        this.posterUrl = posterUrl;
        this.lastSyncedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

package com.savanna.backend.domain;

import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ApplicationMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "announcements")
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

    // 접수 마감 시각
    @Column(nullable = false)
    private LocalDateTime deadline;

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

    // 제한사항
    @Column(columnDefinition = "TEXT")
    private String restrictions;

    // 상금·혜택
    @Column(columnDefinition = "TEXT")
    private String benefits;

    // 문의 이메일
    private String contactEmail;

    // 공고 원문 링크
    @Column(length = 2000)
    private String sourceUrl;

    // 지원 사이트 링크
    @Column(length = 2000)
    private String applyUrl;

    // 참가비: 원화 기준, 무료는 0, 미확인은 null
    @Column(precision = 15, scale = 0)
    private BigDecimal cost;

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
            LocalDateTime deadline,
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
            String restrictions,
            String benefits,
            String contactEmail,
            String sourceUrl,
            String applyUrl,
            BigDecimal cost
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
        this.restrictions = restrictions;
        this.benefits = benefits;
        this.contactEmail = contactEmail;
        this.sourceUrl = sourceUrl;
        this.applyUrl = applyUrl;
        this.cost = cost;
    }

    public void changeVisibility(boolean visibility) {
        this.visibility = visibility;
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
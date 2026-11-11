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
    @Column(length = 512) private String externalCategoryCodes;
    @Column(length = 512) private String primaryCategoryCodes;
    private String classificationIssue;
    @Column(columnDefinition = "TEXT") private String excludeReason;
    private LocalDate apiStartDate;
    private LocalDate apiDeadline;
    private LocalDate csvStartDate;
    private LocalDate csvDeadline;
    private String dateSource;
    @Column(columnDefinition = "TEXT") private String csvMetadata;
    @Column(columnDefinition = "TEXT") private String applicationInstructions;
    @Column(columnDefinition = "TEXT") private String formNote;
    @Column(columnDefinition = "TEXT") private String lengthNote;
    private String teamType;
    private String allowedStatusRaw;
    private String csvRequiredRegionRaw;
    @Column(columnDefinition = "TEXT") private String venueNote;
    private String detailStatus;

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
        if (csvMetadata == null) { this.title = title; this.host = host; }
        this.field = field;
        // API에 시작 시각이 없어 날짜의 시작을 내부 기준으로 사용합니다.
        this.apiStartDate = start;
        this.apiDeadline = deadline;
        if (dateSource == null || "API_ESTIMATED".equals(dateSource)) {
            if (dateSource == null && this.applicationStartsAt != null) dateSource = "MANUAL";
            else { this.applicationStartsAt = start.atStartOfDay(); this.deadline = deadline; dateSource = "API_ESTIMATED"; }
        }
        if (this.enrollmentRequirement == null) this.enrollmentRequirement = target;
        if (this.participationRegion == null) this.participationRegion = participationRegion;
        this.applicationPeriodRaw = periodRaw;
        this.sourceUrl = sourceUrl;
        this.posterUrl = posterUrl;
        this.lastSyncedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
    }

    public boolean isAvailable() { return visibility && excludeReason == null && classificationIssue == null; }

    public void initializeFromCsv(String id, String title, String host, String category, String url, String poster) {
        sourceProvider="CONTEST_KOREA"; externalId=id; this.title=title; this.host=host;
        externalCategory=category; sourceUrl=url; posterUrl=poster;
    }

    public void recordCategory(String observedCode, String primaryCode) {
        var observed = codes(externalCategoryCodes); if (observedCode != null) observed.add(observedCode);
        var primary = codes(primaryCategoryCodes); if (primaryCode != null) primary.add(primaryCode);
        externalCategoryCodes = String.join("|", observed); primaryCategoryCodes = String.join("|", primary);
        if ("UNSUPPORTED_CSV_CATEGORY".equals(classificationIssue)) { field=null; return; }
        var decisive = primary.isEmpty() ? observed : primary;
        if (decisive.size() == 1) {
            field = com.savanna.backend.integration.contestkorea.ContestKoreaCategory.fromSource(decisive.first()).orElseThrow().field();
            classificationIssue = null;
        } else { field = null; classificationIssue = "AMBIGUOUS_EXTERNAL_CATEGORY"; }
    }
    public void flagCsvCategory(String raw, String reason, String metadata) {
        classificationIssue="UNSUPPORTED_CSV_CATEGORY"; field=null; csvMetadata=metadata;
        if(reason!=null&&!reason.isBlank()){excludeReason=reason;visibility=false;}
    }
    public void flagUnsupportedApiCategory() {
        if (!"UNSUPPORTED_CSV_CATEGORY".equals(classificationIssue)) classificationIssue="UNSUPPORTED_API_CATEGORY";
        field=null;
    }
    public void recordCsvCategory(String code) {
        if("UNSUPPORTED_CSV_CATEGORY".equals(classificationIssue))classificationIssue=null;
        recordCategory(code,code);
    }
    private static java.util.TreeSet<String> codes(String value) {
        return value == null || value.isBlank() ? new java.util.TreeSet<>()
                : new java.util.TreeSet<>(java.util.List.of(value.split("\\|")));
    }

    public void supplement(java.util.Map<String,String> values, LocalDate start, LocalDate end, String metadata) {
        if (start != null) { csvStartDate=start; applicationStartsAt=start.atStartOfDay(); dateSource="CSV"; }
        if (end != null) { csvDeadline=end; deadline=end; dateSource="CSV"; }
        if (filled(values,"topic")) theme=values.get("topic");
        if (filled(values,"criteria")) evaluationCriteria=values.get("criteria");
        if (filled(values,"deliverables")) submissionRequirements=values.get("deliverables");
        if (filled(values,"how_to_apply")) applicationInstructions=values.get("how_to_apply");
        if (filled(values,"form_note")) formNote=values.get("form_note");
        if (filled(values,"length_note")) lengthNote=values.get("length_note");
        if (filled(values,"team_type")) teamType=values.get("team_type");
        if (filled(values,"allowed_status")) allowedStatusRaw=values.get("allowed_status");
        if (filled(values,"required_region")) csvRequiredRegionRaw=values.get("required_region");
        if (filled(values,"venue_note")) venueNote=values.get("venue_note");
        if (filled(values,"detail_status")) detailStatus=values.get("detail_status");
        if (filled(values,"team_min")) minMembers=Integer.valueOf(values.get("team_min"));
        if (filled(values,"team_max")) maxMembers=Integer.valueOf(values.get("team_max"));
        if (filled(values,"target_raw")) enrollmentRequirement=values.get("target_raw");
        if (filled(values,"exclude_reason")) { excludeReason=values.get("exclude_reason"); visibility=false; }
        if (posterUrl == null && filled(values,"poster_url")) posterUrl=values.get("poster_url");
        csvMetadata=metadata;
    }
    private static boolean filled(java.util.Map<String,String> values, String key) { return values.get(key)!=null && !values.get(key).isBlank(); }

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

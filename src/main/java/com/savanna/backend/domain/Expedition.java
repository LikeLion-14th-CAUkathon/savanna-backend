package com.savanna.backend.domain;

import com.savanna.backend.enums.ExpeditionStage;
import com.savanna.backend.error.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expeditions", uniqueConstraints = @UniqueConstraint(
        name = "uk_expeditions_team_announcement", columnNames = {"team_id", "announcement_id"}))
public class Expedition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expedition_id")
    private Long expeditionId;
    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false, updatable = false)
    private Announcement announcement;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ExpeditionStage stage = ExpeditionStage.KICKOFF;
    private LocalDate kickoffDate;
    private LocalDate draftDate;
    private LocalDate reviewDate;
    private LocalDate submitTargetDate;
    private LocalDateTime submissionCompletedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Expedition(Long teamId, Announcement announcement) {
        if (teamId == null || teamId <= 0 || announcement == null) invalid();
        this.teamId = teamId;
        this.announcement = announcement;
    }

    public void updateSchedule(LocalDate kickoffDate, LocalDate draftDate,
                               LocalDate reviewDate, LocalDate submitTargetDate) {
        if (announcement.getDeadline() == null) invalid();
        LocalDate previous = null;
        for (LocalDate date : new LocalDate[]{kickoffDate, draftDate, reviewDate, submitTargetDate}) {
            if (date == null) continue;
            if ((previous != null && date.isBefore(previous))
                    || date.isAfter(announcement.getDeadline().toLocalDate())) invalid();
            previous = date;
        }
        this.kickoffDate = kickoffDate; this.draftDate = draftDate;
        this.reviewDate = reviewDate; this.submitTargetDate = submitTargetDate;
    }

    /** 이력 저장은 호출 서비스의 같은 트랜잭션에서 수행합니다. */
    public void changeStage(ExpeditionStage nextStage) {
        if (nextStage == null) invalid();
        if (stage == nextStage) return;
        stage = nextStage;
        submissionCompletedAt = nextStage == ExpeditionStage.SUBMITTED ? now() : null;
    }
    public boolean isSubmissionCompleted() { return submissionCompletedAt != null; }
    @PrePersist private void onCreate() { createdAt = now(); updatedAt = createdAt; }
    @PreUpdate private void onUpdate() { updatedAt = now(); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
    private static void invalid() { throw new BusinessException(ErrorCode.INVALID_REQUEST); }
}

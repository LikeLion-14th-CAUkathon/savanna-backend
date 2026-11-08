package com.savanna.backend.domain;

import com.savanna.backend.enums.ExpeditionStage;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expeditions", uniqueConstraints = @UniqueConstraint(
        name = "uk_expeditions_team_announcement",
        columnNames = {"team_id", "announcement_id"}
))
public class Expedition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expedition_id")
    private Long expeditionId;

    // Team 엔티티가 연결되기 전까지 팀 식별자로 참조합니다.
    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false, updatable = false)
    private Announcement announcement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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
        if (teamId == null || teamId <= 0 || announcement == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.teamId = teamId;
        this.announcement = announcement;
    }

    /**
     * 전체 목표일을 교체합니다. null은 미설정이며, 설정된 날짜끼리 순서를 검증합니다.
     * 검증 실패 시 기존 일정은 변경하지 않습니다.
     */
    public void updateSchedule(LocalDate kickoffDate, LocalDate draftDate,
                               LocalDate reviewDate, LocalDate submitTargetDate) {
        LocalDateTime deadline = announcement.getDeadline();
        if (deadline == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        LocalDate previous = null;
        for (LocalDate date : new LocalDate[]{kickoffDate, draftDate, reviewDate, submitTargetDate}) {
            if (date == null) continue;
            if ((previous != null && date.isBefore(previous)) || date.isAfter(deadline.toLocalDate())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            previous = date;
        }
        this.kickoffDate = kickoffDate;
        this.draftDate = draftDate;
        this.reviewDate = reviewDate;
        this.submitTargetDate = submitTargetDate;
    }

    /**
     * 변경 이력 생성은 호출 서비스가 같은 트랜잭션에서 처리해야 합니다.
     * 제출 단계에서 되돌리면 제출 완료 표시를 취소합니다.
     */
    public void changeStage(ExpeditionStage nextStage) {
        if (nextStage == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (stage == nextStage) return;
        this.stage = nextStage;
        this.submissionCompletedAt = nextStage == ExpeditionStage.SUBMITTED ? now() : null;
    }

    public boolean isSubmissionCompleted() {
        return submissionCompletedAt != null;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}

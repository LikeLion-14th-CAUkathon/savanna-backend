package com.savanna.backend.domain;

import com.savanna.backend.enums.ExpeditionStage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expedition_stage_histories")
public class ExpeditionStageHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stageHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expedition_id", nullable = false, updatable = false)
    private Expedition expedition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ExpeditionStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ExpeditionStage toStage;

    @Column(nullable = false, updatable = false)
    private Long changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public ExpeditionStageHistory(Expedition expedition, ExpeditionStage fromStage,
                                  ExpeditionStage toStage, Long changedBy) {
        this.expedition = expedition;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.changedBy = changedBy;
        this.changedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}

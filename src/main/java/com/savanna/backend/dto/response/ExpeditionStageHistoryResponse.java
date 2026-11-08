package com.savanna.backend.dto.response;

import com.savanna.backend.domain.ExpeditionStageHistory;
import com.savanna.backend.enums.ExpeditionStage;
import java.time.LocalDateTime;

public record ExpeditionStageHistoryResponse(
        Long stageHistoryId, ExpeditionStage fromStage, ExpeditionStage toStage,
        Long changedBy, LocalDateTime changedAt
) {
    public static ExpeditionStageHistoryResponse from(ExpeditionStageHistory h) {
        return new ExpeditionStageHistoryResponse(h.getStageHistoryId(), h.getFromStage(),
                h.getToStage(), h.getChangedBy(), h.getChangedAt());
    }
}

package com.savanna.backend.dto.response;
import com.savanna.backend.domain.ExpeditionChecklist;

public record ExpeditionChecklistResponse(
        Long checklistId, Long expeditionId, String title, boolean isChecked, int sortOrder
) {
    public static ExpeditionChecklistResponse from(ExpeditionChecklist c) {
        return new ExpeditionChecklistResponse(c.getChecklistId(), c.getExpedition().getExpeditionId(),
                c.getTitle(), c.isChecked(), c.getSortOrder());
    }
}

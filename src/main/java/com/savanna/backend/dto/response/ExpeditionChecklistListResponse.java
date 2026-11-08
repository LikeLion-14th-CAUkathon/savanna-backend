package com.savanna.backend.dto.response;
import java.util.List;

public record ExpeditionChecklistListResponse(
        List<ExpeditionChecklistResponse> items, long completedCount, int totalCount, String progress
) {
    public static ExpeditionChecklistListResponse from(List<ExpeditionChecklistResponse> items) {
        long completed = items.stream().filter(ExpeditionChecklistResponse::isChecked).count();
        return new ExpeditionChecklistListResponse(List.copyOf(items), completed, items.size(),
                completed + "/" + items.size());
    }
}

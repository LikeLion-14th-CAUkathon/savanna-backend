package com.savanna.backend.dto.response;

import com.savanna.backend.domain.ExpeditionTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ExpeditionTaskResponse(
        Long taskId, Long expeditionId, String title, LocalDate dueDate,
        LocalTime dueTime, boolean isDone, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static ExpeditionTaskResponse from(ExpeditionTask task) {
        return new ExpeditionTaskResponse(task.getTaskId(), task.getExpedition().getExpeditionId(),
                task.getTitle(), task.getDueDate(), task.getDueTime(), task.isDone(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}

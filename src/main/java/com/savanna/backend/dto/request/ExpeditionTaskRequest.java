package com.savanna.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record ExpeditionTaskRequest(
        @NotBlank @Size(max = 1000) String title,
        @NotNull LocalDate dueDate,
        LocalTime dueTime
) {}

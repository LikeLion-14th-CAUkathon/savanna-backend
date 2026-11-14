package com.savanna.backend.dto.request;

import com.savanna.backend.enums.ExpeditionStage;
import jakarta.validation.constraints.NotNull;

public record ExpeditionStageRequest(@NotNull ExpeditionStage stage) {}

package com.savanna.backend.dto.request;
import jakarta.validation.constraints.*;

public record ExpeditionChecklistRequest(
        @NotBlank @Size(max = 1000) String title,
        @Min(0) Integer sortOrder
) {}

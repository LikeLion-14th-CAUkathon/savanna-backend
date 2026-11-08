package com.savanna.backend.dto.request;
import jakarta.validation.constraints.*;

public record ExpeditionChecklistUpdateRequest(
        @Size(max = 1000) @Pattern(regexp = "(?s).*\\S.*", message = "항목은 공백일 수 없습니다.") String title,
        Boolean isChecked,
        @Min(0) Integer sortOrder
) {}

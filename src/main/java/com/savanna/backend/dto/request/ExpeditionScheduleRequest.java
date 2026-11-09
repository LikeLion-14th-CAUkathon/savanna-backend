package com.savanna.backend.dto.request;

import java.time.LocalDate;

/** PATCH: 생략 또는 null 필드는 기존 날짜를 유지합니다. */
public record ExpeditionScheduleRequest(
        LocalDate kickoffDate, LocalDate draftDate, LocalDate reviewDate, LocalDate submitTargetDate
) {}

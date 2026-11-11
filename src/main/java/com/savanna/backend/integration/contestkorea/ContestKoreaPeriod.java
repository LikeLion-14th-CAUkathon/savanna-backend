package com.savanna.backend.integration.contestkorea;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.regex.Pattern;

public record ContestKoreaPeriod(LocalDate start, LocalDate deadline) {
    private static final Pattern PERIOD = Pattern.compile("^\\s*(\\d{1,2})\\.(\\d{1,2})\\s*[~～〜–—-]\\s*(\\d{1,2})\\.(\\d{1,2})\\s*$");

    public static ContestKoreaPeriod parse(String raw) {
        var match = PERIOD.matcher(raw == null ? "" : raw);
        if (!match.matches()) throw new IllegalArgumentException("접수 기간 형식을 확인해야 합니다.");
        try {
            var start = MonthDay.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
            var end = MonthDay.of(Integer.parseInt(match.group(3)), Integer.parseInt(match.group(4)));
            // 서비스 정책: 시작은 2026년, 연말을 넘기는 마감만 2027년입니다.
            return new ContestKoreaPeriod(LocalDate.of(2026, start.getMonthValue(), start.getDayOfMonth()),
                    LocalDate.of(end.isBefore(start) ? 2027 : 2026, end.getMonthValue(), end.getDayOfMonth()));
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("유효한 접수 날짜가 아닙니다.");
        }
    }
}

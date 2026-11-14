package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.enums.AnnouncementField;

public enum ContestKoreaCategory {
    IDEA("031410001", AnnouncementField.IDEA_ARCHITECTURE_STARTUP),
    IT("030310001", AnnouncementField.ACADEMIC_SCIENCE_IT),
    DESIGN("030610001", AnnouncementField.ART_DESIGN_WEBTOON);

    private final String code;
    private final AnnouncementField field;
    ContestKoreaCategory(String code, AnnouncementField field) { this.code = code; this.field = field; }
    public String code() { return code; }
    public AnnouncementField field() { return field; }

    public static java.util.Optional<ContestKoreaCategory> fromSource(String raw) {
        if (raw == null) return java.util.Optional.empty();
        String source = raw.replaceAll("[\\s•·ㆍ|]", "").toLowerCase(java.util.Locale.ROOT);
        return switch (source) {
            case "it", "학문과학it", "030310001" -> java.util.Optional.of(IT);
            case "idea", "아이디어건축창업", "031410001" -> java.util.Optional.of(IDEA);
            case "design", "미술디자인웹툰", "030610001" -> java.util.Optional.of(DESIGN);
            default -> java.util.Optional.empty();
        };
    }

    public static java.util.List<ContestKoreaCategory> selected(String option) {
        return "ALL".equalsIgnoreCase(option) ? java.util.List.of(IT, IDEA, DESIGN)
                : java.util.List.of(valueOf(option.toUpperCase(java.util.Locale.ROOT)));
    }
}

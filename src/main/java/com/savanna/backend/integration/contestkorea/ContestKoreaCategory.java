package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.enums.AnnouncementField;

public enum ContestKoreaCategory {
    IDEA("031410001", AnnouncementField.IDEA_PLANNING),
    IT("030310001", AnnouncementField.DEVELOPMENT_HACKATHON),
    DESIGN("030610001", AnnouncementField.DESIGN);

    private final String code;
    private final AnnouncementField field;
    ContestKoreaCategory(String code, AnnouncementField field) { this.code = code; this.field = field; }
    public String code() { return code; }
    public AnnouncementField field() { return field; }
}

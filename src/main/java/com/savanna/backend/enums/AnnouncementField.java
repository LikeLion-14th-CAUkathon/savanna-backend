package com.savanna.backend.enums;

@io.swagger.v3.oas.annotations.media.Schema(description = "외부 분야와 동일한 공고 분야: 학문·과학·IT / 아이디어·건축·창업 / 미술·디자인·웹툰")
public enum AnnouncementField {
    ACADEMIC_SCIENCE_IT,          // 학문·과학·IT
    IDEA_ARCHITECTURE_STARTUP,     // 아이디어·건축·창업
    ART_DESIGN_WEBTOON             // 미술·디자인·웹툰
}

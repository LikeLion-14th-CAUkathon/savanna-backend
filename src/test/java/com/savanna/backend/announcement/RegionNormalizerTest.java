package com.savanna.backend.announcement;

import com.savanna.backend.util.RegionNormalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionNormalizerTest {
    @Test void repairsAsciiIdeographicAndNonbreakingSpacesWithinKnownLabels() {
        assertEquals("전국",RegionNormalizer.normalize("전 국"));
        assertEquals("대구",RegionNormalizer.normalize("대\u3000구"));
        assertEquals("전국",RegionNormalizer.normalize("전\u00a0국"));
        assertEquals("경남",RegionNormalizer.normalize("경\u200b남"));
        assertEquals("대구광역시",RegionNormalizer.normalize("대 구 광 역 시"));
        assertEquals("전라남도",RegionNormalizer.normalize("전 라 남 도"));
    }
    @Test void keepsMeaningfulPlaceSeparatorsAndDoesNotRewritePartsOfWords() {
        assertEquals("경기 성남",RegionNormalizer.normalize("경기 성남"));
        assertEquals("경기 성남",RegionNormalizer.normalize("경 기 성남"));
        assertEquals("온라인 · 서울 현장 발표",RegionNormalizer.normalize("온 라 인 · 서 울 현장 발표"));
        assertEquals("서울대 구로 캠퍼스",RegionNormalizer.normalize("서울대 구로 캠퍼스"));
        assertEquals("대구로",RegionNormalizer.normalize("대구로"));
        assertNull(RegionNormalizer.normalize(null));assertNull(RegionNormalizer.normalize(" \u3000\u00a0 "));
    }
    @Test void repeatedNormalizationDoesNotChangeTheValue() {
        String first=RegionNormalizer.normalize("전\u3000국 / 대 구 / 경기 성남");
        assertEquals("전국 / 대구 / 경기 성남",first);
        assertEquals(first,RegionNormalizer.normalize(first));
    }
}

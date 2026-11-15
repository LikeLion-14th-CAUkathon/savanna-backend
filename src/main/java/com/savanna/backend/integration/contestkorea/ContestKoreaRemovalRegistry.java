package com.savanna.backend.integration.contestkorea;

import java.util.Map;

/** Exact source IDs explicitly removed by the user. Never infer deletion from a title or age word. */
public final class ContestKoreaRemovalRegistry {
    private ContestKoreaRemovalRegistry() {}
    public static final Map<String,String> REMOVED=Map.ofEntries(
            Map.entry("202611040033","어린이 미래 상상 그림 공모전"),
            Map.entry("202610170003","2026 ACEL 전국 청소년 미술 공모전"),
            Map.entry("202610080029","2026 세계아동그림대회"),
            Map.entry("202610140076","제3회 생성형 AI 경진대회 (초·중·고 학생문화경진대회)"),
            Map.entry("202610280043","제4회 산업기술 R&D 어린이 그림그리기 공모전"),
            Map.entry("202610010030","제1회 중앙일보 어린이 미술대회"),
            Map.entry("202610280023","2026 한·중·일 3국 초·중·고 포스터 공모전"),
            Map.entry("202610010095","대한민국 국제 어린이 미술 대회 (Korea International Children's Art Competition)"),
            Map.entry("202610090004","2026 제 23회 고양호수만화축제 [청소년만화공모전]"),
            Map.entry("202611020027","2026년 경상북도 도민 대상 규제개선 공모전")
    );
    public static boolean contains(String id) { return id!=null && REMOVED.containsKey(id.trim()); }
}

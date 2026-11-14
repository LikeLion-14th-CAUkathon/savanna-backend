package com.savanna.backend.util;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Repairs spacing inside known region labels, not whitespace between distinct places or prose. */
public final class RegionNormalizer {
    private RegionNormalizer() {}
    private static final Pattern SPACES=Pattern.compile("[\\p{Z}\\s\\u200B\\uFEFF]+");
    private record Label(String value,Pattern pattern) {}
    private static final List<Label> LABELS=List.of(
            "전국","온라인","해외","서울","부산","대구","인천","광주","대전","울산","세종",
            "경기","강원","충북","충남","전북","전남","경북","경남","제주",
            "서울특별시","부산광역시","대구광역시","인천광역시","광주광역시","대전광역시","울산광역시","세종특별자치시",
            "경기도","강원도","강원특별자치도","충청북도","충청남도","전라북도","전북특별자치도","전라남도","경상북도","경상남도","제주도","제주특별자치도",
            "서울시","부산시","대구시","인천시","광주시","대전시","울산시","세종시")
            .stream().sorted(Comparator.comparingInt(String::length).reversed())
            .map(value->new Label(value,Pattern.compile("(?<![\\p{L}\\p{N}])"+
                    value.codePoints().mapToObj(c->Pattern.quote(new String(Character.toChars(c)))).collect(Collectors.joining(" *"))+
                    "(?![\\p{L}\\p{N}])"))).toList();

    public static String normalize(String value) {
        if(value==null)return null;
        String normalized=SPACES.matcher(value).replaceAll(" ").strip();
        if(normalized.isEmpty())return null;
        for(var label:LABELS)normalized=label.pattern().matcher(normalized).replaceAll(label.value());
        return normalized;
    }
}

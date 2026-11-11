package com.savanna.backend.integration.contestkorea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContestKoreaItem(
        @JsonProperty("STR_MEETCODE") String id,
        @JsonProperty("STR_MENU") String category,
        @JsonProperty("STR_MEETING") String title,
        @JsonProperty("STR_HOST") String host,
        @JsonProperty("STR_TARGET") String target,
        @JsonProperty("STR_DATE1") String period,
        @JsonProperty("STR_URL") String sourceUrl,
        @JsonProperty("STR_IMG") String posterUrl,
        @JsonProperty("STR_AREA") String participationRegion) {}

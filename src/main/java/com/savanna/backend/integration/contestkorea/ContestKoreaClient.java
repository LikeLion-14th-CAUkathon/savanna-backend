package com.savanna.backend.integration.contestkorea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContestKoreaClient {
    private final RestClient client;
    private final String apiKey;

    public ContestKoreaClient(@Value("${contest-korea.base-url:https://www.contestkorea.com}") String baseUrl,
                             @Value("${contest-korea.api-key:}") String apiKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(String resultcode, String resultmsg, @JsonProperty("Data") List<ContestKoreaItem> data) {}

    public List<ContestKoreaItem> fetch(ContestKoreaCategory category) {
        if (category == null) throw new IllegalArgumentException("공고 분야가 필요합니다.");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("ContestKorea API 키가 설정되지 않았습니다.");
        Envelope[] response;
        try {
            response = client.get().uri(builder -> builder.path("/api/meeting_info_api.php")
                    .queryParam("apikey", apiKey).queryParam("Txt_bcode", category.code())
                    .queryParam("limitsu", 100).build()).retrieve().body(Envelope[].class);
        } catch (RuntimeException exception) {
            // 외부 요청 URL에 키가 포함되므로 원본 예외/본문을 전달하거나 기록하지 않습니다.
            throw new IllegalStateException("ContestKorea 공고 조회에 실패했습니다.");
        }
        if (response == null || response.length == 0) throw new IllegalStateException("ContestKorea 응답이 비어 있습니다.");
        var items = new ArrayList<ContestKoreaItem>();
        for (var envelope : response) {
            if (envelope == null || !"1".equals(envelope.resultcode()) || envelope.data() == null) {
                throw new IllegalStateException("ContestKorea 응답 결과를 확인할 수 없습니다.");
            }
            items.addAll(envelope.data());
        }
        return List.copyOf(items);
    }
}

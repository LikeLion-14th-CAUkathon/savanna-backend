package com.savanna.backend.service;

import com.savanna.backend.enums.RecommendationSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.IntStream;

/** Explicit opt-in preview data, never silently presented as a real AI recommendation. */
@Component
@Profile("(local | test) & recommendation-mock & !prod & !production")
public class MockRecommendationAiClient implements RecommendationAiClient {
    @Override public List<Suggestion> recommend(TeamInput team, List<Candidate> candidates) {
        return IntStream.range(0, Math.min(5, candidates.size())).mapToObj(i -> new Suggestion(
                candidates.get(i).announcementId(), 90 - i,
                "[테스트용 추천] 팀 프로젝트와의 적합도를 확인할 예시 공고입니다. 실제 AI 추천 이유가 아닙니다.")).toList();
    }
    @Override public RecommendationSource source() { return RecommendationSource.MOCK; }
}

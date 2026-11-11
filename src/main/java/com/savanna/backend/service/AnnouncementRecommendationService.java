package com.savanna.backend.service;

import com.savanna.backend.dto.response.AnnouncementRecommendationResponse;
import com.savanna.backend.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnnouncementRecommendationService {
    private final RecommendationStore store;
    private final ObjectProvider<RecommendationAiClient> aiClient;

    public List<AnnouncementRecommendationResponse> list(Long teamId, Principal principal) {
        return store.list(teamId, principal);
    }

    // Intentionally no transaction: external AI calls must not hold database locks/connections.
    public List<AnnouncementRecommendationResponse> refresh(Long teamId, Principal principal) {
        var context = store.prepare(teamId, principal, true);
        if (context.candidates().isEmpty()) return store.replace(context, List.of(), com.savanna.backend.enums.RecommendationSource.AI, principal);
        var client = aiClient.getIfAvailable();
        if (client == null) throw new BusinessException(ErrorCode.RECOMMENDATION_AI_UNAVAILABLE);
        List<RecommendationAiClient.Suggestion> suggestions;
        com.savanna.backend.enums.RecommendationSource source;
        try { suggestions = client.recommend(context.teamInput(), context.candidates()); source = client.source(); }
        catch (RuntimeException exception) { throw new BusinessException(ErrorCode.RECOMMENDATION_AI_UNAVAILABLE); }
        var candidateIds = new HashSet<Long>();
        context.candidates().forEach(c -> candidateIds.add(c.announcementId()));
        var seen = new HashSet<Long>();
        if (source == null || suggestions == null || suggestions.size() > candidateIds.size()) invalid();
        for (var suggestion : suggestions) {
            if (suggestion == null || !candidateIds.contains(suggestion.announcementId()) || !seen.add(suggestion.announcementId())
                    || suggestion.score() < 0 || suggestion.score() > 100 || suggestion.reason() == null
                    || suggestion.reason().isBlank() || suggestion.reason().strip().length() > 500) invalid();
        }
        var candidates = new HashMap<Long, RecommendationAiClient.Candidate>();
        context.candidates().forEach(c -> candidates.put(c.announcementId(), c));
        var best = suggestions.stream().sorted(Comparator.comparingInt(RecommendationAiClient.Suggestion::score).reversed()
                .thenComparing(s -> candidates.get(s.announcementId()).deadline())
                .thenComparing(RecommendationAiClient.Suggestion::announcementId)).limit(5).toList();
        return store.replace(context, best, source, principal);
    }

    private static void invalid() { throw new BusinessException(ErrorCode.RECOMMENDATION_AI_INVALID_RESPONSE); }
}

package com.savanna.backend.announcement;

import com.savanna.backend.service.MockRecommendationAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationMockProfileTest {
    @Test void mockRequiresExplicitLocalOrTestOptInAndIsDisabledInProduction() {
        for (String[] profiles : new String[][]{{"local"}, {"recommendation-mock"}, {"prod", "local", "recommendation-mock"}, {"production", "test", "recommendation-mock"}}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profiles); context.register(MockRecommendationAiClient.class); context.refresh();
                assertTrue(context.getBeansOfType(MockRecommendationAiClient.class).isEmpty());
            }
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local", "recommendation-mock");
            context.register(MockRecommendationAiClient.class); context.refresh();
            assertEquals(1, context.getBeansOfType(MockRecommendationAiClient.class).size());
            assertEquals(com.savanna.backend.enums.RecommendationSource.MOCK, context.getBean(MockRecommendationAiClient.class).source());
        }
    }
}

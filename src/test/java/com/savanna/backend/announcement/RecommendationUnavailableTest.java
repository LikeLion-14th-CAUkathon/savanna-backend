package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.AnnouncementRecommendationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class RecommendationUnavailableTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired AnnouncementRecommendationRepository recommendations;

    @Test void missingAiReturns503ButExistingCacheRemainsReadable() throws Exception {
        var user = new User("no-ai@example.com", "no-ai"); em.persist(user);
        var team = new Team("팀", "프로젝트", "소개", null, null, null); em.persist(team);
        em.persist(new TeamMember(user, team, TeamRole.MEMBER));
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        var announcement = Announcement.builder().title("저장된 추천").deadline(now.toLocalDate().plusDays(2))
                .applicationStartsAt(now.minusDays(1)).build(); em.persist(announcement);
        recommendations.saveAndFlush(new AnnouncementRecommendation(team, announcement, 80, "기존 추천 이유", "old", "old", RecommendationSource.AI, now));
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String path = "/api/v1/teams/" + team.getTeamId() + "/recommendations";
        String bearer = "Bearer " + tokens.createAccessToken(user.getUserId());
        mvc.perform(post(path + "/refresh").header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RECOMMENDATION_AI_UNAVAILABLE"));
        mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("기존 추천 이유"));
        assertEquals(1, recommendations.count());
    }
}

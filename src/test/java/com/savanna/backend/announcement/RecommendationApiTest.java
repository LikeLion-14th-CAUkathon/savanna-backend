package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(RecommendationApiTest.AiConfiguration.class)
@Transactional
class RecommendationApiTest {
    @TestConfiguration static class AiConfiguration {
        @Bean FakeAi fakeAi() { return new FakeAi(); }
    }
    static class FakeAi implements RecommendationAiClient {
        int calls;
        List<Candidate> seen;
        TeamInput team;
        Function<List<Candidate>, List<Suggestion>> behavior;
        Runnable duringCall;
        RecommendationSource source = RecommendationSource.AI;
        void reset() {
            calls = 0; seen = List.of(); duringCall = () -> {}; source = RecommendationSource.AI;
            behavior = candidates -> java.util.stream.IntStream.range(0, candidates.size()).mapToObj(i ->
                    new Suggestion(candidates.get(i).announcementId(), 95 - i, "프로젝트와 공고 주제가 연결됩니다.")).toList();
        }
        public List<Suggestion> recommend(TeamInput team, List<Candidate> candidates) {
            calls++; seen = candidates; this.team = team; duringCall.run(); return behavior.apply(candidates);
        }
        @Override public RecommendationSource source() { return source; }
    }

    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired AnnouncementRecommendationRepository recommendations;
    @Autowired FakeAi ai;
    MockMvc mvc;
    User member, outsider;
    Team team;
    LocalDate today;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ai.reset();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        member = new User("recommend-member@example.com", "recommend-member");
        member.updateProfile("팀원", "대학", EnrollmentStatus.ENROLLED);
        outsider = new User("recommend-outsider@example.com", "recommend-outsider");
        em.persist(member); em.persist(outsider);
        team = new Team("팀", "수면 습관 코칭", "청년 생활 문제를 해결하는 프로젝트", null, null, "Java");
        em.persist(team);
        em.persist(new TeamMember(member, team, TeamRole.MEMBER));
        em.flush();
    }

    String path() { return "/api/v1/teams/" + team.getTeamId() + "/recommendations"; }
    String bearer(User user) { return "Bearer " + tokens.createAccessToken(user.getUserId()); }
    Announcement announcement(String title, int days, String target) {
        var announcement = Announcement.builder().title(title).host("주최").field(AnnouncementField.IDEA_PLANNING)
                .applicationStartsAt(today.minusDays(1).atStartOfDay()).deadline(today.plusDays(days))
                .minMembers(1).maxMembers(3).enrollmentRequirement(target).theme("청년 생활 문제").build();
        em.persist(announcement); em.flush(); return announcement;
    }
    void refresh() throws Exception {
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member))).andExpect(status().isOk());
    }

    @Test void generatesPersistsAndReturnsTopFiveAndGetNeverCallsAi() throws Exception {
        for (int i = 0; i < 7; i++) announcement("공고 " + i, i, "재학생");
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].title").value("공고 0"))
                .andExpect(jsonPath("$[0].score").value(95))
                .andExpect(jsonPath("$[0].deadline").value(today.toString()))
                .andExpect(jsonPath("$[0].source").value("AI"))
                .andExpect(jsonPath("$[0].eligibility.status").value("SATISFIED"))
                .andExpect(jsonPath("$[0].stale").value(false));
        assertEquals(5, recommendations.count());
        assertEquals("수면 습관 코칭", ai.team.projectName());
        em.flush(); em.clear();
        mvc.perform(get(path()).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].reason").value("프로젝트와 공고 주제가 연결됩니다."));
        assertEquals(1, ai.calls);
        refresh();
        assertEquals(5, recommendations.count());
    }

    @Test void filtersClosedPrivateUpcomingUnknownStartAndMismatchedBeforeAiButKeepsUnknownConditions() throws Exception {
        var valid = announcement("오늘 마감", 0, "재학생");
        var unknown = announcement("별도 조건", 1, "누구나, 해당자 ▶");
        announcement("마감", -1, "재학생");
        announcement("비공개", 1, "재학생").changeVisibility(false);
        announcement("불일치", 1, "졸업생");
        var future = Announcement.builder().title("접수 예정").deadline(today.plusDays(3))
                .applicationStartsAt(today.plusDays(1).atStartOfDay()).build();
        em.persist(future);
        em.persist(Announcement.builder().title("시작 미확인").deadline(today.plusDays(3)).build());
        refresh();
        assertEquals(Set.of(valid.getAnnouncementId(), unknown.getAnnouncementId()),
                new HashSet<>(ai.seen.stream().map(RecommendationAiClient.Candidate::announcementId).toList()));
        assertEquals(EligibilityStatus.TEAM_INFO_REQUIRED, ai.seen.get(1).eligibility().status());
    }

    @Test void equalScoresUseDeadlineThenIdAndChangingTeamMarksCacheStale() throws Exception {
        var later = announcement("나중 마감", 3, "재학생");
        var first = announcement("먼저", 1, "재학생");
        announcement("둘째", 1, "재학생");
        ai.behavior = candidates -> candidates.stream().map(c -> new RecommendationAiClient.Suggestion(c.announcementId(), 80, "추천 이유")).toList();
        refresh();
        team.update(null, null, "바뀐 프로젝트 소개", null, null, null);
        mvc.perform(get(path()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$[0].announcementId").value(first.getAnnouncementId()))
                .andExpect(jsonPath("$[2].announcementId").value(later.getAnnouncementId()))
                .andExpect(jsonPath("$[0].stale").value(true));
        assertEquals(1, ai.calls);
    }

    @Test void changedAnnouncementMarksStaleAndPrivateOrNewMismatchIsFilteredOnRead() throws Exception {
        var visible = announcement("공개", 1, "재학생");
        var hidden = announcement("숨김", 2, "재학생");
        refresh();
        hidden.changeVisibility(false);
        visible.syncContestKorea("changed", "아이디어", "바뀐 제목", "주최", AnnouncementField.IDEA_PLANNING,
                today.minusDays(1), today.plusDays(1), "재학생", "온라인", "11.01~11.30", "https://example.com", null);
        mvc.perform(get(path()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].stale").value(true));
        member.updateProfile(null, EnrollmentStatus.GRADUATED);
        mvc.perform(get(path()).header("Authorization", bearer(member))).andExpect(content().json("[]"));
        assertEquals(2, recommendations.count());
        assertEquals(1, ai.calls);
    }

    @Test void aiFailureAndInvalidResultsKeepPreviousRecommendations() throws Exception {
        var notice = announcement("공고", 1, "재학생");
        refresh();
        var id = recommendations.findAll().get(0).getRecommendationId();
        ai.behavior = candidates -> { throw new IllegalStateException("private-provider-information"); };
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RECOMMENDATION_AI_UNAVAILABLE"));
        var invalidResponses = new ArrayList<List<RecommendationAiClient.Suggestion>>();
        invalidResponses.add(null);
        invalidResponses.add(Arrays.asList((RecommendationAiClient.Suggestion) null));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(Long.MAX_VALUE, 80, "이유")));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 101, "이유")));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), -1, "이유")));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 80, " ")));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 80, "가".repeat(501))));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 80, null)));
        invalidResponses.add(List.of(new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 80, "이유"),
                new RecommendationAiClient.Suggestion(notice.getAnnouncementId(), 70, "중복")));
        for (var invalid : invalidResponses) {
            ai.behavior = candidates -> invalid;
            mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member))).andExpect(status().isBadGateway());
            assertEquals(id, recommendations.findAll().get(0).getRecommendationId());
        }
    }

    @Test void teamChangesDuringAiReturnConflictWithoutReplacingCache() throws Exception {
        announcement("공고", 1, "재학생");
        refresh();
        var id = recommendations.findAll().get(0).getRecommendationId();
        ai.duringCall = () -> team.update(null, null, "AI 호출 중 수정", null, null, null);
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member))).andExpect(status().isConflict());
        assertEquals(id, recommendations.findAll().get(0).getRecommendationId());
    }

    @Test void announcementChangesDuringAiReturnConflictWithoutReplacingCache() throws Exception {
        var notice = announcement("공고", 1, "재학생");
        refresh();
        var id = recommendations.findAll().get(0).getRecommendationId();
        ai.duringCall = () -> notice.changeVisibility(false);
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(member))).andExpect(status().isConflict());
        assertEquals(id, recommendations.findAll().get(0).getRecommendationId());
    }

    @Test void noCandidatesReturnsEmptyAndDoesNotCallAi() throws Exception {
        mvc.perform(get(path()).header("Authorization", bearer(member))).andExpect(content().json("[]"));
        refresh();
        assertEquals(0, ai.calls);
        assertEquals(0, recommendations.count());
    }

    @Test void verifiesAuthenticationMembershipAndIdsBeforeAi() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path() + "/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(post(path() + "/refresh").header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teams/9223372036854775807/recommendations").header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
        for (String id : List.of("0", "-1", "invalid")) {
            mvc.perform(get("/api/v1/teams/" + id + "/recommendations").header("Authorization", bearer(member)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, ai.calls);
    }
}

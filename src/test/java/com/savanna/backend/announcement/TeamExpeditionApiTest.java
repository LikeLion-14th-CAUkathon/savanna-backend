package com.savanna.backend.announcement;

import com.savanna.backend.controller.TeamExpeditionController;
import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.error.GlobalExceptionHandler;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.ExpeditionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ExpeditionTestTeamConfig.class)
@Transactional
class TeamExpeditionApiTest {
    @Autowired ExpeditionService service;
    @Autowired ExpeditionRepository expeditions;
    @Autowired AnnouncementRepository announcements;
    @Autowired EntityManager em;
    MockMvc mvc;
    int userSequence;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TeamExpeditionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    User user() {
        int id = ++userSequence;
        var user = new User("oasis" + id + "@example.com", "oasis-sub-" + id);
        em.persist(user);
        return user;
    }

    Expedition expedition(Long team, String title, LocalDateTime deadline) {
        var a = announcements.saveAndFlush(Announcement.builder().title(title).deadline(deadline.toLocalDate()).build());
        return expeditions.saveAndFlush(new Expedition(team, a));
    }

    Proposal proposal(Expedition expedition, User sender) {
        var proposal = new Proposal(expedition, sender, "함께 참여해요");
        em.persist(proposal);
        return proposal;
    }

    void recipient(Proposal p, User user, ProposalRecipientStatus status) {
        var recipient = new ProposalRecipient(p, user);
        if (status != ProposalRecipientStatus.PENDING) recipient.respond(status);
        em.persist(recipient);
    }

    @Test void returnsMapFieldsAndZeroWhenNoOneHasJoined() throws Exception {
        var e = expedition(1L, "공고명", LocalDateTime.of(2026, 12, 30, 18, 0));
        e.changeStage(ExpeditionStage.DRAFT);
        em.flush();
        em.clear();
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expeditionId").value(e.getExpeditionId()))
                .andExpect(jsonPath("$[0].announcementId").isNumber())
                .andExpect(jsonPath("$[0].title").value("공고명"))
                .andExpect(jsonPath("$[0].deadline").value("2026-12-30"))
                .andExpect(jsonPath("$[0].stage").value("DRAFT"))
                .andExpect(jsonPath("$[0].joinedCount").value(0));
    }

    @Test void countsOnlyJoinedRecipientsOfTheExpeditionProposal() throws Exception {
        var e = expedition(1L, "공고", LocalDateTime.now().plusDays(10));
        var sender = user();
        var joined = user();
        var secondJoined = user();
        var p1 = proposal(e, sender);
        recipient(p1, joined, ProposalRecipientStatus.JOINED);
        recipient(p1, secondJoined, ProposalRecipientStatus.JOINED);
        recipient(p1, user(), ProposalRecipientStatus.PENDING);
        recipient(p1, user(), ProposalRecipientStatus.DECLINED);
        em.flush();
        em.clear();
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].joinedCount").value(2));
    }

    @Test void countsDoNotLeakBetweenExpeditionsOrTeams() throws Exception {
        var deadline = LocalDateTime.now().plusDays(10);
        var first = expedition(1L, "첫 원정", deadline);
        var second = expedition(1L, "둘째 원정", deadline.plusDays(1));
        var other = expedition(2L, "다른 팀", deadline);
        var sender = user();
        var joined = user();
        recipient(proposal(first, sender), joined, ProposalRecipientStatus.JOINED);
        recipient(proposal(other, sender), joined, ProposalRecipientStatus.JOINED);
        em.flush();
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].joinedCount").value(1))
                .andExpect(jsonPath("$[1].joinedCount").value(0));
    }

    @Test void ordersByDeadlineThenIdAndIncludesClosedSubmittedAndHiddenAnnouncements() throws Exception {
        var deadline = LocalDateTime.now().minusDays(2);
        expedition(1L, "나중 마감", deadline.plusDays(1));
        var first = expedition(1L, "먼저 마감", deadline);
        var second = expedition(1L, "같은 마감", deadline);
        first.changeStage(ExpeditionStage.SUBMITTED);
        second.getAnnouncement().changeVisibility(false);
        em.flush();
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("먼저 마감"))
                .andExpect(jsonPath("$[0].stage").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].title").value("같은 마감"))
                .andExpect(jsonPath("$[2].title").value("나중 마감"));
    }

    @Test void authorizedTeamWithoutExpeditionsReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void validatesIdAuthenticationAndMembershipEvenForEmptyTeam() throws Exception {
        mvc.perform(get("/api/v1/teams/0/expeditions").principal(() -> "member"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teams/abc/expeditions").principal(() -> "member"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/teams/1/expeditions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teams/1/expeditions").principal(() -> "outsider"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teams/2/expeditions").principal(() -> "member"))
                .andExpect(status().isForbidden());
    }
}

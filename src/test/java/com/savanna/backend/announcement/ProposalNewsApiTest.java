package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ProposalNewsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired ProposalRecipientRepository recipients;
    @Autowired TeamMemberRepository members;
    MockMvc mvc;
    User sender, receiver, outsider;
    Team team;
    Long proposalId, recipientId, expeditionId;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sender = user("sender"); receiver = user("receiver"); outsider = user("outsider");
        team = new Team("팀", "프로젝트", "소개", null, null, null); em.persist(team);
        em.persist(new TeamMember(sender, team, TeamRole.LEADER));
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        em.flush();
        // 실제 제안 전송 API로 원정·제안·수신 기록을 생성합니다.
        var response = createProposal();
        proposalId = response[0]; expeditionId = response[1];
        recipientId = recipients.findReceivedProposals(receiver.getUserId()).get(0).getProposalRecipientId();
        em.flush(); em.clear();
    }

    User user(String name) {
        var user = new User("news-" + name + "@example.com", "news-sub-" + name);
        user.updateProfile(name, "대학", EnrollmentStatus.ENROLLED); em.persist(user); return user;
    }
    String bearer(User user) { return "Bearer " + tokens.createAccessToken(user.getUserId()); }
    String recipientPath() { return "/api/v1/proposal-recipients/" + recipientId; }
    String responsePath() { return "/api/v1/proposals/" + proposalId + "/recipients"; }
    Long[] createProposal() throws Exception {
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        var notice = Announcement.builder().title("참여 공고").applicationStartsAt(now.minusDays(1))
                .deadline(now.toLocalDate().plusDays(10)).build(); em.persist(notice); em.flush();
        var result = mvc.perform(post("/api/v1/announcements/" + notice.getAnnouncementId() + "/proposals")
                        .header("Authorization", bearer(sender)).contentType("application/json")
                        .content("{\"message\":\"같이 참가해요\",\"teamId\":" + team.getTeamId() + "}"))
                .andExpect(status().isCreated()).andReturn();
        var data = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        return new Long[]{data.get("proposalId").asLong(), data.get("expeditionId").asLong()};
    }
    void dot(User user, boolean hasUnread, int unreadProposals, int unreadResponses) throws Exception {
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasUnread").value(hasUnread))
                .andExpect(jsonPath("$.unreadProposalCount").value(unreadProposals))
                .andExpect(jsonPath("$.unreadResponseCount").value(unreadResponses));
    }
    void respond(String value, int version) throws Exception {
        mvc.perform(patch(recipientPath() + "/response").header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value))
                .andExpect(jsonPath("$.responseVersion").value(version));
    }
    String ackBody(long version) {
        return "{\"responses\":[{\"proposalRecipientId\":" + recipientId + ",\"responseVersion\":" + version + "}]}";
    }
    void acknowledge(long version, int changed) throws Exception {
        mvc.perform(patch(responsePath() + "/read").header("Authorization", bearer(sender))
                        .contentType("application/json").content(ackBody(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedCount").value(changed));
    }
    ProposalRecipient saved() { em.flush(); em.clear(); return em.find(ProposalRecipient.class, recipientId); }

    @Test void canonicalPathsReplaceOldAliasesAndNotificationsAreAuthenticated() throws Exception {
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].proposalRecipientId").value(recipientId));
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(1));
        for (String old : new String[]{"/api/v1/proposals/received", "/api/v1/proposals/received/pending-count"}) {
            mvc.perform(get(old).header("Authorization", bearer(receiver))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/proposals/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(patch(recipientPath() + "/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch(responsePath() + "/read").contentType("application/json").content(ackBody(1)))
                .andExpect(status().isUnauthorized());
    }

    @Test void readingPersistsFirstTimestampAndClearsDotWithoutChangingPendingCount() throws Exception {
        dot(receiver, true, 1, 0); dot(sender, false, 0, 0); dot(outsider, false, 0, 0);
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$[0].readAt").isEmpty());
        dot(receiver, true, 1, 0);
        mvc.perform(patch(recipientPath() + "/read").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));
        var firstRead = saved().getReadAt();
        assertNotNull(firstRead);
        mvc.perform(patch(recipientPath() + "/read").header("Authorization", bearer(receiver))).andExpect(status().isOk());
        assertEquals(firstRead, saved().getReadAt());
        assertNull(saved().getRespondedAt());
        dot(receiver, false, 0, 0);
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test void changesParticipationBothWaysAndKeepsMembershipRoleAndCurrentCounts() throws Exception {
        respond("JOINED", 1);
        var joinedTime = saved().getRespondedAt();
        dot(receiver, false, 0, 0); dot(sender, true, 0, 1);
        mvc.perform(get(responsePath()).header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.joinedCount").value(1))
                .andExpect(jsonPath("$.recipients[0].responseVersion").value(1))
                .andExpect(jsonPath("$.recipients[0].responseUnread").value(true));
        dot(sender, true, 0, 1); // GET 자체로 확인 처리하지 않습니다.
        acknowledge(1, 1); dot(sender, false, 0, 0);
        respond("JOINED", 1); // 재전송은 시간·버전·알림을 바꾸지 않습니다.
        assertEquals(joinedTime, saved().getRespondedAt());
        dot(sender, false, 0, 0);
        respond("DECLINED", 2);
        assertFalse(saved().getRespondedAt().isBefore(joinedTime));
        dot(sender, true, 0, 1);
        mvc.perform(get(responsePath()).header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.joinedCount").value(0)).andExpect(jsonPath("$.declinedCount").value(1))
                .andExpect(jsonPath("$.pendingCount").value(0));
        mvc.perform(get("/api/v1/expeditions/" + expeditionId + "/proposals").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$[0].recipients[0].status").value("DECLINED"));
        mvc.perform(get("/api/v1/teams/" + team.getTeamId() + "/expeditions").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$[0].joinedCount").value(0));
        assertEquals(TeamRole.MEMBER, members.findByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()).orElseThrow().getRole());
        acknowledge(2, 1); dot(sender, false, 0, 0);
        respond("JOINED", 3);
        dot(sender, true, 0, 1);
        mvc.perform(get("/api/v1/teams/" + team.getTeamId() + "/expeditions").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$[0].joinedCount").value(1));
        assertEquals(2, members.findAllByTeam_TeamId(team.getTeamId()).size());
    }

    @Test void acknowledgingOldObservedVersionDoesNotHideNewResponseAndDotCombinesBothSources() throws Exception {
        respond("JOINED", 1);
        respond("DECLINED", 2);
        acknowledge(1, 1); // 1을 본 동안 2가 도착한 경우를 재현합니다.
        assertEquals(1, saved().getResponseReadVersion());
        assertEquals(2, saved().getResponseVersion());
        dot(sender, true, 0, 1);
        acknowledge(1, 0); dot(sender, true, 0, 1);
        acknowledge(2, 1); dot(sender, false, 0, 0);
        // sender도 다른 제안을 받았다면 응답 확인 후에도 새 제안 때문에 점이 유지됩니다.
        var original = em.find(ProposalRecipient.class, recipientId).getProposal();
        var anotherNotice = Announcement.builder().title("다른 제안").deadline(LocalDate.now().plusDays(10)).build(); em.persist(anotherNotice);
        var anotherExpedition = new Expedition(team.getTeamId(), anotherNotice); em.persist(anotherExpedition);
        var anotherProposal = new Proposal(anotherExpedition, receiver, "새 제안"); em.persist(anotherProposal);
        em.persist(new ProposalRecipient(anotherProposal, sender)); em.flush();
        respond("JOINED", 3); dot(sender, true, 1, 1);
        acknowledge(3, 1); dot(sender, true, 1, 0);
    }

    @Test void enforcesOwnershipAndRejectsInvalidOrFutureAcknowledgementsWithoutClearingNews() throws Exception {
        mvc.perform(patch(recipientPath() + "/read").header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/proposal-recipients/9223372036854775807/read").header("Authorization", bearer(receiver)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/proposal-recipients/0/read").header("Authorization", bearer(receiver))).andExpect(status().isBadRequest());
        respond("JOINED", 1);
        mvc.perform(patch(responsePath() + "/read").header("Authorization", bearer(receiver)).contentType("application/json").content(ackBody(1)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(responsePath() + "/read").header("Authorization", bearer(sender)).contentType("application/json").content(ackBody(99)))
                .andExpect(status().isConflict());
        for (String invalid : new String[]{"{}", "{\"responses\":[]}", ackBody(0), "{\"responses\":[null]}"}) {
            mvc.perform(patch(responsePath() + "/read").header("Authorization", bearer(sender)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        dot(sender, true, 0, 1);
    }

    @Test void invalidBatchCannotAcknowledgeResponsesBelongingToAnotherProposal() throws Exception {
        respond("JOINED", 1);
        createProposal();
        Long anotherRecipient = recipients.findReceivedProposals(receiver.getUserId()).get(0).getProposalRecipientId();
        String body = "{\"responses\":[{\"proposalRecipientId\":" + recipientId + ",\"responseVersion\":1},"
                + "{\"proposalRecipientId\":" + anotherRecipient + ",\"responseVersion\":1}]}";
        mvc.perform(patch(responsePath() + "/read").header("Authorization", bearer(sender)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, em.find(ProposalRecipient.class, recipientId).getResponseReadVersion());
    }

    @Test void legacyResponseWithZeroVersionCanBeAcknowledgedWithoutDroppingFutureChanges() throws Exception {
        em.createNativeQuery("update proposal_recipients set status='JOINED', responded_at=CURRENT_TIMESTAMP, response_version=0 where proposal_recipient_id=:id")
                .setParameter("id", recipientId).executeUpdate();
        em.clear();
        dot(sender, true, 0, 1);
        acknowledge(1, 1); dot(sender, false, 0, 0);
        respond("DECLINED", 2); dot(sender, true, 0, 1);
    }
}

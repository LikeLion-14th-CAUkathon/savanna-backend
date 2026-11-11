package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ProposalRecipientApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    @Autowired TeamMemberRepository members;
    MockMvc mvc;
    User sender, receiver, outsider;
    Team team;
    int sequence;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sender = user("sender");
        receiver = user("receiver");
        outsider = user("outsider");
        team = new Team("팀", "프로젝트", "소개", null, null, null);
        em.persist(team);
        em.persist(new TeamMember(sender, team, TeamRole.LEADER));
        em.flush();
    }

    User user(String suffix) {
        var user = new User("response-" + suffix + "@example.com", "response-sub-" + suffix);
        user.updateProfile(suffix, "대학", EnrollmentStatus.ENROLLED);
        em.persist(user);
        return user;
    }

    String bearer(User user) { return "Bearer " + tokens.createAccessToken(user.getUserId()); }

    ProposalRecipient invitation(User user) {
        var announcement = Announcement.builder().title("제안 공고 " + ++sequence)
                .deadline(LocalDateTime.now().plusDays(10).toLocalDate()).build();
        em.persist(announcement);
        var expedition = new Expedition(team.getTeamId(), announcement);
        em.persist(expedition);
        var proposal = new Proposal(expedition, sender, "함께 해요");
        em.persist(proposal);
        var recipient = new ProposalRecipient(proposal, user);
        em.persist(recipient);
        em.flush();
        return recipient;
    }

    String responsePath(ProposalRecipient recipient) {
        return "/api/v1/proposal-recipients/" + recipient.getProposalRecipientId() + "/response";
    }

    @Test void listsOnlyOwnProposalsInLatestOrderAndCountsOnlyPending() throws Exception {
        var first = invitation(receiver);
        first.respond(ProposalRecipientStatus.DECLINED);
        var latest = invitation(receiver);
        invitation(outsider);
        em.flush();
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].proposalRecipientId").value(latest.getProposalRecipientId()))
                .andExpect(jsonPath("$[0].announcementTitle").value("제안 공고 2"))
                .andExpect(jsonPath("$[0].senderName").value("sender"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(1));
        assertNull(latest.getReadAt());
    }

    @Test void emptyInboxReturnsEmptyListAndZero() throws Exception {
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(0));
    }

    @Test void joinedAddsMembershipAndAllowsChangingResponseWithoutLeavingTeam() throws Exception {
        var recipient = invitation(receiver);
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
        em.flush();
        em.clear();
        assertEquals(TeamRole.MEMBER, members.findByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()).orElseThrow().getRole());
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.pendingCount").value(0));
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseVersion").value(2));
        assertEquals(ProposalRecipientStatus.DECLINED, em.find(ProposalRecipient.class, recipient.getProposalRecipientId()).getStatus());
        assertTrue(members.existsByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()));
    }

    @Test void existingLeaderKeepsRoleWhenJoiningAndDeclineDoesNotRemoveMembership() throws Exception {
        var recipient = invitation(sender);
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(sender))
                        .contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isOk());
        assertEquals(1, members.findAllByTeam_TeamId(team.getTeamId()).size());
        assertEquals(TeamRole.LEADER, members.findByUser_UserIdAndTeam_TeamId(sender.getUserId(), team.getTeamId()).orElseThrow().getRole());
        var decline = invitation(sender);
        mvc.perform(patch(responsePath(decline)).header("Authorization", bearer(sender))
                        .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isOk());
        assertTrue(members.existsByUser_UserIdAndTeam_TeamId(sender.getUserId(), team.getTeamId()));
    }

    @Test void declineStoresResponseWithoutJoining() throws Exception {
        var recipient = invitation(receiver);
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
        assertFalse(members.existsByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()));
    }

    @Test void requiresAuthenticationAndRejectsInvalidResponses() throws Exception {
        var recipient = invitation(receiver);
        mvc.perform(get("/api/v1/proposal-recipients/received")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/proposal-recipients/pending-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch(responsePath(recipient)).contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isUnauthorized());
        for (String body : new String[]{"{}", "{\"status\":\"PENDING\"}", "{\"status\":\"UNKNOWN\"}"}) {
            mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(receiver))
                            .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/proposal-recipients/0/response").header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void otherUsersCannotRespondAndMissingRecipientReturns404() throws Exception {
        var recipient = invitation(receiver);
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(outsider))
                        .contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isForbidden());
        assertEquals(ProposalRecipientStatus.PENDING, recipient.getStatus());
        mvc.perform(patch("/api/v1/proposal-recipients/9223372036854775807/response").header("Authorization", bearer(receiver))
                        .contentType("application/json").content("{\"status\":\"JOINED\"}"))
                .andExpect(status().isNotFound());
    }
}

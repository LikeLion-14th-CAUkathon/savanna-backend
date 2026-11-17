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
    @Autowired com.savanna.backend.service.TeamService teamService;
    @Autowired com.savanna.backend.service.TeamInvitationService invitations;
    @Autowired ProposalRecipientRepository recipients;
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

    @Test
    void listsOnlyOwnProposalsInLatestOrderAndCountsOnlyPending() throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        em.flush();

        var first = invitation(receiver);
        first.respond(ProposalRecipientStatus.DECLINED);

        var latest = invitation(receiver);
        invitation(outsider);
        em.flush();

        mvc.perform(get("/api/v1/proposal-recipients/received")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].proposalRecipientId")
                        .value(latest.getProposalRecipientId()))
                .andExpect(jsonPath("$[0].announcementTitle")
                        .value("제안 공고 2"))
                .andExpect(jsonPath("$[0].senderName").value("sender"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(get("/api/v1/proposal-recipients/pending-count")
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1));

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

    @Test void leavingDeletesAllProposalResponsesAndSentProposalsWhileKeepingExpeditions() {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var pending = invitation(receiver);
        var declined = invitation(receiver);
        declined.respond(ProposalRecipientStatus.DECLINED);
        var joined = invitation(receiver);
        joined.respond(ProposalRecipientStatus.JOINED);
        Long expeditionId = joined.getProposal().getExpedition().getExpeditionId();
        em.flush();

        teamService.leaveTeam(receiver.getUserId(), team.getTeamId());
        em.clear();

        // 탈퇴한 팀원의 모든 응답 내역 삭제 확인
        assertNull(em.find(ProposalRecipient.class, pending.getProposalRecipientId()));
        assertNull(em.find(ProposalRecipient.class, declined.getProposalRecipientId()));
        assertNull(em.find(ProposalRecipient.class, joined.getProposalRecipientId()));
        assertFalse(members.existsByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()));
        // 오아시스 원정(공고)은 유지 확인
        assertNotNull(em.find(Expedition.class, expeditionId));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ProposalRecipientStatus.class, names = {"JOINED", "DECLINED"})
    void formerMemberCannotReuseDeletedResponse(ProposalRecipientStatus requestedStatus) throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var recipient = invitation(receiver);
        recipient.respond(ProposalRecipientStatus.DECLINED);
        em.flush();
        teamService.leaveTeam(receiver.getUserId(), team.getTeamId());
        em.clear();

        // 레코드가 삭제되었으므로 404 반환
        mvc.perform(patch(responsePath(recipient)).header("Authorization", bearer(receiver))
                        .contentType("application/json")
                        .content("{\"status\":\"" + requestedStatus.name() + "\"}"))
                .andExpect(status().isNotFound());

        assertFalse(members.existsByUser_UserIdAndTeam_TeamId(receiver.getUserId(), team.getTeamId()));
        assertNull(em.find(ProposalRecipient.class, recipient.getProposalRecipientId()));
    }

    @Test void leavingAndMovingTeamsDeletesOldReceivedProposalsAndNotifications() throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var oldResponse = invitation(receiver);
        oldResponse.respond(ProposalRecipientStatus.DECLINED);
        invitation(receiver);
        em.flush();

        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.unreadResponseCount").value(1));
        teamService.leaveTeam(receiver.getUserId(), team.getTeamId());
        em.clear();

        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.pendingCount").value(0));
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.hasUnread").value(false));
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.unreadResponseCount").value(0));
        mvc.perform(patch("/api/v1/proposal-recipients/{id}/read", oldResponse.getProposalRecipientId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isNotFound());

        var newTeam = new Team("새 팀", "프로젝트", "소개", null, null, null);
        em.persist(newTeam);
        sender = em.find(User.class, outsider.getUserId());
        em.persist(new TeamMember(sender, newTeam, TeamRole.LEADER));
        em.persist(new TeamMember(em.find(User.class, receiver.getUserId()), newTeam, TeamRole.MEMBER));
        team = newTeam;
        var current = invitation(em.find(User.class, receiver.getUserId()));

        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proposalRecipientId").value(current.getProposalRecipientId()));
        mvc.perform(get("/api/v1/proposal-recipients/pending-count").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.pendingCount").value(1));
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(receiver)))
                .andExpect(jsonPath("$.unreadProposalCount").value(1));
    }

    @Test void invitationRejoinResetsHistoryAndRegistersExistingProposalsAsPendingAgain() throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var history = invitation(receiver);
        history.respond(ProposalRecipientStatus.JOINED);
        var pending = invitation(receiver);
        var historyProposalId = history.getProposal().getProposalId();
        var pendingProposalId = pending.getProposal().getProposalId();
        em.flush();

        var link = invitations.createInvitation(sender.getUserId(), team.getTeamId());
        teamService.leaveTeam(receiver.getUserId(), team.getTeamId());
        invitations.acceptInvitation(receiver.getUserId(), link.token());
        em.flush();
        em.clear();

        // 탈퇴 시 기존 기록은 삭제되었고, 재가입 후 팀의 기존 제안 2건 모두 초기화된 PENDING 상태로 재등록됨
        assertNull(em.find(ProposalRecipient.class, history.getProposalRecipientId()));
        var active = recipients.findReceivedProposals(receiver.getUserId());
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(r -> r.getStatus() == ProposalRecipientStatus.PENDING));
        assertEquals(0, recipients.countUnreadResponses(sender.getUserId()));
        assertTrue(recipients.countDistinctUsersByTeamAndStatus(team.getTeamId(), ProposalRecipientStatus.JOINED).isEmpty());
        assertEquals(0, recipients.countJoinedForExpedition(history.getProposal().getExpedition().getExpeditionId(), team.getTeamId()));
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
    @Test void formerSenderDetachesFromSentProposalsWhileKeepingExpeditionsAndOtherResponses() throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var recipient = invitation(receiver);
        recipient.respond(ProposalRecipientStatus.JOINED);
        Long proposalId = recipient.getProposal().getProposalId();
        Long expeditionId = recipient.getProposal().getExpedition().getExpeditionId();
        em.flush();

        mvc.perform(get("/api/v1/proposals/sent").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.unreadResponseCount").value(1));

        teamService.leaveTeam(sender.getUserId(), team.getTeamId());
        em.clear();

        // 제안과 남은 팀원의 응답, 오아시스 원정은 유지되고 발신자(sender)만 null 처리됨
        var savedProposal = em.find(Proposal.class, proposalId);
        assertNotNull(savedProposal);
        assertNull(savedProposal.getSender());
        assertNotNull(em.find(ProposalRecipient.class, recipient.getProposalRecipientId()));
        assertNotNull(em.find(Expedition.class, expeditionId));

        mvc.perform(get("/api/v1/proposals/sent").header("Authorization", bearer(sender)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/proposals/notifications").header("Authorization", bearer(sender)))
                .andExpect(jsonPath("$.unreadResponseCount").value(0));
    }

    @Test void formerSenderCannotDirectlyReadOldResponseDetailsOrAcknowledgeThem() throws Exception {
        em.persist(new TeamMember(receiver, team, TeamRole.MEMBER));
        var recipient = invitation(receiver);
        recipient.respond(ProposalRecipientStatus.JOINED);
        em.flush();
        Long proposalId = recipient.getProposal().getProposalId();
        teamService.leaveTeam(sender.getUserId(), team.getTeamId());
        em.clear();

        mvc.perform(get("/api/v1/proposals/{id}/recipients", proposalId).header("Authorization", bearer(sender)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/proposals/{id}/recipients/read", proposalId)
                        .header("Authorization", bearer(sender)).contentType("application/json")
                        .content("{\"responses\":[{\"proposalRecipientId\":" + recipient.getProposalRecipientId()
                                + ",\"responseVersion\":1}]}"))
                .andExpect(status().isForbidden());
        assertEquals(0, em.find(ProposalRecipient.class, recipient.getProposalRecipientId()).getResponseReadVersion());
    }
}
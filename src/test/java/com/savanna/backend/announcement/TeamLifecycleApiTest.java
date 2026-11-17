package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class TeamLifecycleApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired TeamMemberRepository members;
    @Autowired TeamInvitationRepository invitations;
    MockMvc mvc;User leader,first,last,outsider;long teamId;String inviteUrl;
    @BeforeEach void setup()throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader=user("lifecycle-leader");first=user("lifecycle-first");last=user("lifecycle-last");outsider=user("lifecycle-outsider");em.flush();
        var created=create(leader);teamId=created.get("teamId").asLong();inviteUrl=created.get("inviteUrl").asText();
        var team=em.find(Team.class,teamId);
        var firstMember=new TeamMember(first,team,TeamRole.MEMBER);ReflectionTestUtils.setField(firstMember,"joinedAt",LocalDateTime.of(2026,1,1,1,0));em.persist(firstMember);
        var lastMember=new TeamMember(last,team,TeamRole.MEMBER);ReflectionTestUtils.setField(lastMember,"joinedAt",LocalDateTime.of(2026,1,2,1,0));em.persist(lastMember);em.flush();
    }
    User user(String name){var u=new User(name+"@example.invalid",name);u.updateProfile(name,"대학",EnrollmentStatus.ENROLLED);u.updateCharacter(1);em.persist(u);return u;}
    String auth(User u){return "Bearer "+tokens.createAccessToken(u.getUserId());}
    String leave(){return "/api/v1/teams/"+teamId+"/members/me";}
    JsonNode create(User u)throws Exception {
        var result=mvc.perform(post("/api/v1/teams").header("Authorization",auth(u)).contentType("application/json").content("{\"name\":\"자동 링크 팀\",\"projectName\":\"프로젝트\",\"projectIntro\":\"소개\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.inviteUrl").isString()).andReturn();
        return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("data");
    }
    @Test void teamCreationAlreadyHasExactlyOneSharedLinkAndManualCreateReusesIt()throws Exception {
        assertTrue(inviteUrl.startsWith("https://"));assertEquals(1,invitations.findAllByTeam_TeamId(teamId).size());
        em.flush();em.clear();
        for(User user:new User[]{leader,first,last})
            mvc.perform(get("/api/v1/teams/{id}/invitations/current",teamId).header("Authorization",auth(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteUrl").value(inviteUrl));
        mvc.perform(post("/api/v1/teams/{id}/invitations",teamId).header("Authorization",auth(leader))).andExpect(status().isCreated()).andExpect(jsonPath("$.data.inviteUrl").value(inviteUrl));
        assertEquals(1,invitations.findAllByTeam_TeamId(teamId).size());
    }
    @Test void memberCanLeaveLosesAccessAndCanCreateAnotherTeam()throws Exception {
        mvc.perform(delete(leave()).header("Authorization",auth(last))).andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(last.getUserId())).andExpect(jsonPath("$.data.newLeaderUserId").value(org.hamcrest.Matchers.nullValue()));
        assertFalse(members.existsByUser_UserIdAndTeam_TeamId(last.getUserId(),teamId));
        mvc.perform(get("/api/v1/teams").header("Authorization",auth(last))).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/teams/{id}",teamId).header("Authorization",auth(last))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teams/{id}/invitations/current",teamId).header("Authorization",auth(last))).andExpect(status().isForbidden());
        assertNotEquals(teamId,create(last).get("teamId").asLong());
    }
    @Test void leavingLeaderPromotesEarliestJoinerAndPreservesSharedLink()throws Exception {
        mvc.perform(delete(leave()).header("Authorization",auth(leader))).andExpect(status().isOk()).andExpect(jsonPath("$.data.newLeaderUserId").value(first.getUserId()));
        em.flush();em.clear();
        assertFalse(members.existsByUser_UserIdAndTeam_TeamId(leader.getUserId(),teamId));
        assertEquals(TeamRole.LEADER,members.findByUser_UserIdAndTeam_TeamId(first.getUserId(),teamId).orElseThrow().getRole());
        assertEquals(TeamRole.MEMBER,members.findByUser_UserIdAndTeam_TeamId(last.getUserId(),teamId).orElseThrow().getRole());
        mvc.perform(get("/api/v1/teams/{id}/invitations/current",teamId).header("Authorization",auth(first))).andExpect(jsonPath("$.data.inviteUrl").value(inviteUrl));
        mvc.perform(patch("/api/v1/teams/{id}",teamId).header("Authorization",auth(first)).contentType("application/json").content("{\"name\":\"승계된 팀\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/teams/{id}",teamId).header("Authorization",auth(leader)).contentType("application/json").content("{\"name\":\"수정 불가\"}")).andExpect(status().isForbidden());
    }
    @Test void equalJoinTimesUseMembershipIdAndLastMemberDeletesTeam()throws Exception {
        var firstMember=members.findByUser_UserIdAndTeam_TeamId(first.getUserId(),teamId).orElseThrow();
        var lastMember=members.findByUser_UserIdAndTeam_TeamId(last.getUserId(),teamId).orElseThrow();
        ReflectionTestUtils.setField(lastMember,"joinedAt",firstMember.getJoinedAt());assertTrue(firstMember.getTeamMemberId()<lastMember.getTeamMemberId());
        mvc.perform(delete(leave()).header("Authorization",auth(leader))).andExpect(jsonPath("$.data.newLeaderUserId").value(first.getUserId()));
        mvc.perform(delete(leave()).header("Authorization",auth(last))).andExpect(status().isOk());
        mvc.perform(delete(leave()).header("Authorization",auth(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.teamDeleted").value(true)).andExpect(jsonPath("$.data.newLeaderUserId").value(org.hamcrest.Matchers.nullValue()));
        assertNull(em.find(Team.class,teamId));assertEquals(0,members.countByTeam_TeamId(teamId));assertTrue(invitations.findAllByTeam_TeamId(teamId).isEmpty());
    }
    @Test void leaveRequiresAuthenticationMembershipAndValidTeam()throws Exception {
        mvc.perform(delete(leave())).andExpect(status().isUnauthorized());
        mvc.perform(delete(leave()).header("Authorization",auth(outsider))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/teams/9223372036854775807/members/me").header("Authorization",auth(first))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/teams/0/members/me").header("Authorization",auth(first))).andExpect(status().isBadRequest());
        assertEquals(3,members.countByTeam_TeamId(teamId));
    }
    @Test void lastLeaveDeletesAllScopedRecordsAndPreservesOtherTeamAndSharedAnnouncement()throws Exception {
        long otherTeamId=create(outsider).get("teamId").asLong();
        var announcement=Announcement.builder().title("공유 공고").deadline(LocalDate.now().plusDays(10)).build();em.persist(announcement);
        var expedition=new Expedition(teamId,announcement);em.persist(expedition);
        var otherExpedition=new Expedition(otherTeamId,announcement);em.persist(otherExpedition);
        var proposal=new Proposal(expedition,leader,"제안");em.persist(proposal);
        var recipient=new ProposalRecipient(proposal,last);em.persist(recipient);
        var task=new ExpeditionTask(expedition,"일정",LocalDate.now(),null);em.persist(task);
        var checklist=new ExpeditionChecklist(expedition,"제출물",0);em.persist(checklist);
        var history=new ExpeditionStageHistory(expedition,ExpeditionStage.KICKOFF,ExpeditionStage.DRAFT,leader.getUserId());em.persist(history);
        var recommendation=new AnnouncementRecommendation(em.find(Team.class,teamId),announcement,80,"추천","input","announcement",RecommendationSource.MOCK,LocalDateTime.now());em.persist(recommendation);
        em.flush();
        mvc.perform(delete(leave()).header("Authorization",auth(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.teamDeleted").value(false));
        mvc.perform(delete(leave()).header("Authorization",auth(last))).andExpect(status().isOk());
        mvc.perform(delete(leave()).header("Authorization",auth(leader))).andExpect(status().isOk()).andExpect(jsonPath("$.data.teamDeleted").value(true));
        em.flush();em.clear();
        assertNull(em.find(Team.class,teamId));assertNull(em.find(Expedition.class,expedition.getExpeditionId()));
        assertNull(em.find(Proposal.class,proposal.getProposalId()));assertNull(em.find(ProposalRecipient.class,recipient.getProposalRecipientId()));
        assertNull(em.find(ExpeditionTask.class,task.getTaskId()));assertNull(em.find(ExpeditionChecklist.class,checklist.getChecklistId()));
        assertNull(em.find(ExpeditionStageHistory.class,history.getStageHistoryId()));assertNull(em.find(AnnouncementRecommendation.class,recommendation.getRecommendationId()));
        assertTrue(invitations.findAllByTeam_TeamId(teamId).isEmpty());assertEquals(0,members.countByTeam_TeamId(teamId));
        assertNotNull(em.find(Team.class,otherTeamId));assertNotNull(em.find(Expedition.class,otherExpedition.getExpeditionId()));
        assertNotNull(em.find(Announcement.class,announcement.getAnnouncementId()));assertNotNull(em.find(User.class,leader.getUserId()));
        String token=inviteUrl.substring(inviteUrl.lastIndexOf('/')+1);
        mvc.perform(get("/api/v1/invitations/{token}",token)).andExpect(status().isNotFound());
        assertNotEquals(teamId,create(leader).get("teamId").asLong());
    }
    @Test void leaveDeletesProposalResponseHistoryAndRemovesActiveExpeditionCount()throws Exception {
        var announcement=Announcement.builder().title("탈퇴 이력 검증").deadline(LocalDate.now().plusDays(10)).build();em.persist(announcement);
        var expedition=new Expedition(teamId,announcement);em.persist(expedition);var proposal=new Proposal(expedition,leader,"같이 해요");em.persist(proposal);
        var recipient=new ProposalRecipient(proposal,last);recipient.respond(ProposalRecipientStatus.JOINED);em.persist(recipient);em.flush();
        Long recipientId=recipient.getProposalRecipientId();em.clear();
        mvc.perform(get("/api/v1/teams/{id}/expeditions",teamId).header("Authorization",auth(leader))).andExpect(jsonPath("$[0].joinedCount").value(1));
        mvc.perform(delete(leave()).header("Authorization",auth(last))).andExpect(status().isOk());em.flush();em.clear();
        assertNull(em.find(ProposalRecipient.class,recipientId));
        assertNotNull(em.find(Expedition.class,expedition.getExpeditionId()));
        mvc.perform(get("/api/v1/teams/{id}/expeditions",teamId).header("Authorization",auth(leader))).andExpect(jsonPath("$[0].joinedCount").value(0));
        mvc.perform(get("/api/v1/expeditions/{id}",expedition.getExpeditionId()).header("Authorization",auth(last))).andExpect(status().isForbidden());
    }
    @Test void swaggerShowsAutoInvitationAndLeaveResponseAndDeletion()throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.TeamCreateResponseDto.properties.inviteUrl").exists())
            .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/members/me'].delete.responses['200'].content['application/json'].schema").exists())
            .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/members/me'].delete.responses['409']").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.TeamLeaveResponseDto.properties.newLeaderUserId").exists())
            .andExpect(jsonPath("$.components.schemas.TeamLeaveResponseDto.properties.teamDeleted").exists());
    }
}

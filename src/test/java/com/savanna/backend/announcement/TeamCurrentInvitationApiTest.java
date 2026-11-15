package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.invitation.InvitationTokenProvider;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.TeamInvitationRepository;
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
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class TeamCurrentInvitationApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider jwt;
    @Autowired InvitationTokenProvider tokens;
    @Autowired TeamInvitationRepository invitations;
    MockMvc mvc;User leader,member,outsider;Team team;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader=user("current-leader");member=user("current-member");outsider=user("current-outsider");
        team=new Team("team","project","intro",null,null,null);em.persist(team);
        em.persist(new TeamMember(leader,team,TeamRole.LEADER));em.persist(new TeamMember(member,team,TeamRole.MEMBER));em.flush();
    }
    User user(String name){var u=new User(name+"@example.invalid",name);u.updateProfile(name,"대학",EnrollmentStatus.ENROLLED);em.persist(u);return u;}
    String auth(User user){return "Bearer "+jwt.createAccessToken(user.getUserId());}
    String base(){return "/api/v1/teams/"+team.getTeamId()+"/invitations";}
    JsonNode data(org.springframework.test.web.servlet.MvcResult result) throws Exception {return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("data");}
    JsonNode create() throws Exception {return data(mvc.perform(post(base()).header("Authorization",auth(leader))).andExpect(status().isCreated()).andReturn());}
    @Test void allMembersRetrieveSamePersistedLinkWithoutCreatingOrRotatingIt() throws Exception {
        mvc.perform(get(base()+"/current").header("Authorization",auth(member))).andExpect(status().isNotFound());
        assertTrue(invitations.findAllByTeam_TeamId(team.getTeamId()).isEmpty());
        var created=create();em.flush();em.clear();
        assertTrue(created.get("inviteUrl").asText().startsWith("https://savanna-frontend.vercel.app/invite/"));
        assertEquals("ACTIVE",created.get("status").asText());assertTrue(created.get("expiresAt").isNull());
        for(User user:new User[]{leader,member}){
            var current=data(mvc.perform(get(base()+"/current").header("Authorization",auth(user))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn());
            assertEquals(created,current);
        }
        assertEquals(created,create());assertEquals(1,invitations.findAllByTeam_TeamId(team.getTeamId()).size());
        var list=data(mvc.perform(get(base()).header("Authorization",auth(leader))).andExpect(status().isOk()).andReturn());
        assertEquals(created.get("token"),list.get(0).get("token"));assertEquals(created.get("inviteUrl"),list.get(0).get("inviteUrl"));
    }
    @Test void retrievalRequiresMembershipWhileCreationHistoryAndRevocationRemainLeaderOnly() throws Exception {
        var created=create();
        mvc.perform(get(base()+"/current")).andExpect(status().isUnauthorized());
        mvc.perform(get(base()+"/current").header("Authorization",auth(outsider))).andExpect(status().isForbidden());
        mvc.perform(post(base()).header("Authorization",auth(member))).andExpect(status().isForbidden());
        mvc.perform(get(base()).header("Authorization",auth(member))).andExpect(status().isForbidden());
        mvc.perform(delete(base()+"/"+created.get("invitationId").asLong()).header("Authorization",auth(member))).andExpect(status().isForbidden());
    }
    @Test void revokedLinkIsNotReturnedAndLeaderCanCreateNewSharedLink() throws Exception {
        var old=create();
        mvc.perform(delete(base()+"/"+old.get("invitationId").asLong()).header("Authorization",auth(leader))).andExpect(status().isOk());
        mvc.perform(get(base()+"/current").header("Authorization",auth(member))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/invitations/{token}",old.get("token").asText())).andExpect(status().isGone());
        var next=create();assertNotEquals(old.get("token"),next.get("token"));
        var current=data(mvc.perform(get(base()+"/current").header("Authorization",auth(member))).andExpect(status().isOk()).andReturn());assertEquals(next,current);
        var list=data(mvc.perform(get(base()).header("Authorization",auth(leader))).andExpect(status().isOk()).andReturn());
        assertEquals("REVOKED",list.get(1).get("status").asText());assertTrue(list.get(1).get("token").isNull());assertTrue(list.get(1).get("inviteUrl").isNull());
    }
    @Test void legacyExpiredStatusIsRealButPermanentInvitationsNeverExpireNaturally() throws Exception {
        var permanent=create();
        var stored=invitations.findById(permanent.get("invitationId").asLong()).orElseThrow();
        ReflectionTestUtils.setField(stored,"expiresAt",Instant.now().minusSeconds(10));
        var expired=new TeamInvitation(team,leader,tokens.hashToken("legacy-expired-test"));ReflectionTestUtils.setField(expired,"expiresAt",Instant.now().minusSeconds(10));em.persist(expired);
        var activeLegacy=new TeamInvitation(team,leader,tokens.hashToken("legacy-active-test"));em.persist(activeLegacy);em.flush();em.clear();
        var list=data(mvc.perform(get(base()).header("Authorization",auth(leader))).andExpect(status().isOk()).andReturn());
        boolean foundExpired=false;
        for(var item:list){
            if(item.get("invitationId").asLong()==expired.getInvitationId()) {foundExpired=true;assertEquals("EXPIRED",item.get("status").asText());assertFalse(item.get("expiresAt").isNull());assertTrue(item.get("token").isNull());}
            if(item.get("invitationId").asLong()==activeLegacy.getInvitationId()){assertEquals("ACTIVE",item.get("status").asText());assertTrue(item.get("token").isNull());assertTrue(item.get("inviteUrl").isNull());}
        }
        assertTrue(foundExpired);
        mvc.perform(get("/api/v1/invitations/legacy-expired-test")).andExpect(status().isGone());
        mvc.perform(post("/api/v1/invitations/legacy-expired-test/accept").header("Authorization",auth(outsider))).andExpect(status().isGone());
        mvc.perform(get(base()+"/current").header("Authorization",auth(member))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE")).andExpect(jsonPath("$.data.expiresAt").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void swaggerDocumentsSharedUrlAndAllThreeStatuses() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/invitations/current'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/invitations/{token}'].get.responses['410']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/invitations/{token}/accept'].post.responses['410']").exists())
            .andExpect(jsonPath("$.components.schemas.TeamInvitationCreateResponseDto.properties.inviteUrl").exists())
            .andExpect(jsonPath("$.components.schemas.TeamInvitationListResponseDto.properties.token").exists())
            .andExpect(jsonPath("$.components.schemas.TeamInvitationListResponseDto.properties.status.enum",org.hamcrest.Matchers.containsInAnyOrder("ACTIVE","REVOKED","EXPIRED")));
    }
}

package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.invitation.InvitationTokenProvider;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class SingleTeamMembershipApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider jwt;
    @Autowired InvitationTokenProvider invitationTokens;
    @Autowired TeamMemberRepository members;
    @Autowired TeamRepository teams;
    MockMvc mvc;User leader,joiner;Team first,second;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader=user("single-leader");joiner=user("single-joiner");
        first=team("first");second=team("second");
        em.persist(new TeamMember(leader,first,TeamRole.LEADER));em.flush();
    }
    User user(String name){var u=new User(name+"@example.invalid",name);u.updateProfile(name,"대학",EnrollmentStatus.ENROLLED);u.updateCharacter(1);em.persist(u);return u;}
    Team team(String name){var t=new Team(name,"project","intro",null,null,null);em.persist(t);return t;}
    String auth(User user){return "Bearer "+jwt.createAccessToken(user.getUserId());}
    String invitation(Team t){String seed=invitationTokens.createSeed(),token=invitationTokens.createToken(seed);em.persist(new TeamInvitation(t,leader,invitationTokens.hashToken(token),seed));em.flush();return token;}
    ProposalRecipient proposal(User receiver,Team t){
        var a=Announcement.builder().title("single-team").deadline(LocalDate.now().plusDays(10)).build();em.persist(a);
        var e=new Expedition(t.getTeamId(),a);em.persist(e);var p=new Proposal(e,leader,"join");em.persist(p);
        var r=new ProposalRecipient(p,receiver);em.persist(r);em.flush();return r;
    }
    @Test void existingMemberCannotCreateAnotherTeam() throws Exception {
        long before=teams.count();
        mvc.perform(post("/api/v1/teams").header("Authorization",auth(leader)).contentType("application/json")
            .content("{\"name\":\"another\",\"projectName\":\"p\",\"projectIntro\":\"i\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        assertEquals(before,teams.count());assertEquals(1,members.findAllByUser_UserId(leader.getUserId()).size());
    }
    @Test void invitationAllowsFirstJoinButBlocksSameAndOtherTeam() throws Exception {
        String firstToken=invitation(first),secondToken=invitation(second);
        mvc.perform(post("/api/v1/invitations/{token}/accept",firstToken).header("Authorization",auth(joiner))).andExpect(status().isOk());
        for(String token:new String[]{firstToken,secondToken})
            mvc.perform(post("/api/v1/invitations/{token}/accept",token).header("Authorization",auth(joiner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        assertEquals(1,members.findAllByUser_UserId(joiner.getUserId()).size());
    }
    @Test void crossTeamJoinDoesNotChangeResponseReadTimeOrVersion() throws Exception {
        var recipient=proposal(leader,second);
        mvc.perform(patch("/api/v1/proposal-recipients/{id}/response",recipient.getProposalRecipientId()).header("Authorization",auth(leader)).contentType("application/json").content("{\"status\":\"JOINED\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_IN_TEAM"));
        assertEquals(ProposalRecipientStatus.PENDING,recipient.getStatus());assertNull(recipient.getRespondedAt());assertNull(recipient.getReadAt());assertEquals(0,recipient.getResponseVersion());
    }
    @Test void sameTeamResponsesRemainChangeableWithoutChangingMembership() throws Exception {
        var recipient=proposal(leader,first);
        for(String status:new String[]{"JOINED","DECLINED","JOINED"})
            mvc.perform(patch("/api/v1/proposal-recipients/{id}/response",recipient.getProposalRecipientId()).header("Authorization",auth(leader)).contentType("application/json").content("{\"status\":\""+status+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
        assertEquals(1,members.findAllByUser_UserId(leader.getUserId()).size());assertEquals(TeamRole.LEADER,members.findAllByUser_UserId(leader.getUserId()).get(0).getRole());
    }
}

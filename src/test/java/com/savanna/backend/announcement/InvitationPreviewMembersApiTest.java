package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.service.TeamInvitationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class InvitationPreviewMembersApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired TeamInvitationService invitations;
    @Autowired TeamMemberRepository members;
    @Autowired JwtTokenProvider jwt;
    MockMvc mvc;User leader,member,newcomer;Team team;String token;Long invitationId;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader=user("팀장","preview-leader",2);member=user("팀원","preview-member",null);newcomer=user("새 팀원","preview-new",7);
        team=new Team("미리보기 팀","프로젝트","소개",null,null,null);em.persist(team);
        em.persist(new TeamMember(leader,team,TeamRole.LEADER));em.persist(new TeamMember(member,team,TeamRole.MEMBER));em.flush();
        var invitation=invitations.createInvitation(leader.getUserId(),team.getTeamId());token=invitation.token();invitationId=invitation.invitationId();em.flush();em.clear();
    }
    User user(String name,String sub,Integer character){var u=new User(sub+"@example.invalid",sub);u.updateProfile(name,"대학",EnrollmentStatus.ENROLLED);if(character!=null)u.updateCharacter(character);em.persist(u);return u;}
    String path(){return "/api/v1/invitations/"+token;}
    String auth(User user){return "Bearer "+jwt.createAccessToken(user.getUserId());}
    @Test void publicPreviewReturnsOnlyRequestedMemberFieldsIncludingNullCharacter()throws Exception {
        var result=mvc.perform(get(path())).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentMemberCount").value(2))
            .andExpect(jsonPath("$.data.expectedMemberCount").value(3)).andExpect(jsonPath("$.data.members.length()").value(2))
            .andExpect(jsonPath("$.data.members[0].name").value("팀장")).andExpect(jsonPath("$.data.members[0].characterId").value(2))
            .andExpect(jsonPath("$.data.members[1].name").value("팀원")).andExpect(jsonPath("$.data.members[1].characterId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.members[0].userId").doesNotExist()).andExpect(jsonPath("$.data.members[0].email").doesNotExist()).andReturn();
        var roster=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("data").get("members");
        assertTrue(roster.get(1).has("characterId"));assertEquals(2,roster.get(0).size());assertEquals(2,roster.get(1).size());
    }
    @Test void joiningLeavingAndProfileChangesAreReflectedOnNextPreview()throws Exception {
        mvc.perform(post(path()+"/accept").header("Authorization",auth(newcomer))).andExpect(status().isOk());
        mvc.perform(get(path())).andExpect(jsonPath("$.data.members.length()").value(3)).andExpect(jsonPath("$.data.members[2].characterId").value(7));
        mvc.perform(delete("/api/v1/teams/{id}/members/me",team.getTeamId()).header("Authorization",auth(member))).andExpect(status().isOk());
        em.find(User.class,leader.getUserId()).updateCharacter(9);em.flush();em.clear();
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentMemberCount").value(2))
            .andExpect(jsonPath("$.data.members[*].name",org.hamcrest.Matchers.contains("팀장","새 팀원")))
            .andExpect(jsonPath("$.data.members[0].characterId").value(9));
    }
    @Test void emptyLegacyRosterIsEmptyArrayAndInvalidOrRevokedTokensDoNotExposeMembers()throws Exception {
        members.deleteAll(members.findAllByTeam_TeamId(team.getTeamId()));members.flush();
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentMemberCount").value(0)).andExpect(jsonPath("$.data.members").isEmpty());
        em.find(TeamInvitation.class,invitationId).revoke();em.flush();
        mvc.perform(get(path())).andExpect(status().isGone()).andExpect(jsonPath("$.data.members").doesNotExist());
        mvc.perform(get("/api/v1/invitations/unknown-preview-token")).andExpect(status().isNotFound()).andExpect(jsonPath("$.data.members").doesNotExist());
    }
    @Test void swaggerDocumentsMembersNameAndNullableCharacter()throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.TeamInvitationPreviewResponseDto.properties.members.items").exists())
            .andExpect(jsonPath("$.components.schemas.InvitationMember.properties.name").exists())
            .andExpect(jsonPath("$.components.schemas.InvitationMember.properties.characterId").exists());
    }
}

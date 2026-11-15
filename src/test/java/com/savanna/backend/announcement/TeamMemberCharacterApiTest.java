package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="recommendation.ai.enabled=false") @Transactional
class TeamMemberCharacterApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    MockMvc mvc;
    User owner,member;
    Team team;
    final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner=new User("character-owner@example.invalid","character-owner");owner.updateProfile("팀장","대학교",EnrollmentStatus.ENROLLED);
        member=new User("character-member@example.invalid","character-member");member.updateProfile("팀원","대학교",EnrollmentStatus.ON_LEAVE);
        em.persist(owner);em.persist(member);
        team=new Team("팀","프로젝트","소개",null,null,null);em.persist(team);
        em.persist(new TeamMember(owner,team,TeamRole.LEADER));em.persist(new TeamMember(member,team,TeamRole.MEMBER));
        em.persist(new UserRegion(owner,"SEOUL"));em.flush();
    }
    private String bearer(){return "Bearer "+tokens.createAccessToken(owner.getUserId());}
    private String patchBody(Object character){var values=new LinkedHashMap<String,Object>();values.put("name","팀장");values.put("characterId",character);return json.writeValueAsString(values);}

    @Test void selectedCharacterPersistsAndIsReturnedInMeAndTeamMembers() throws Exception {
        long userId=owner.getUserId(),teamId=team.getTeamId();
        mvc.perform(patch("/api/v1/me").header("Authorization",bearer()).contentType("application/json").content(patchBody(3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characterId").value(3));
        em.flush();em.clear();
        mvc.perform(get("/api/v1/me").header("Authorization",bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterId").value(3)).andExpect(jsonPath("$.data.regions[0]").value("SEOUL"));
        var response=mvc.perform(get("/api/v1/teams/{id}",teamId).header("Authorization",bearer()))
                .andExpect(status().isOk()).andReturn().getResponse();
        for(var entry:json.readTree(response.getContentAsString()).get("data").get("members")) {
            assertTrue(entry.has("characterId"));
            if(entry.get("userId").asLong()==userId)assertEquals(3,entry.get("characterId").asInt());
            else assertTrue(entry.get("characterId").isNull());
        }
    }
    @Test void membersUseTheirOwnCharacterRatherThanViewersCharacter() throws Exception {
        owner.updateCharacter(2);member.updateCharacter(9);em.flush();
        var response=mvc.perform(get("/api/v1/teams/{id}",team.getTeamId()).header("Authorization",bearer()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var values=new HashMap<Long,Integer>();
        for(var entry:json.readTree(response.getContentAsString()).get("data").get("members"))values.put(entry.get("userId").asLong(),entry.get("characterId").asInt());
        assertEquals(2,values.get(owner.getUserId()));assertEquals(9,values.get(member.getUserId()));
    }
    @Test void characterIsRequiredAndMissingNullOrInvalidIdsDoNotOverwriteSavedSelection() throws Exception {
        owner.updateCharacter(4);em.flush();
        for(String body:new String[]{"{\"name\":\"팀장\"}",patchBody(null)}) {
            mvc.perform(patch("/api/v1/me").header("Authorization",bearer()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("characterId"));
        }
        for(int invalid:new int[]{-1,0,10})mvc.perform(patch("/api/v1/me").header("Authorization",bearer())
                        .contentType("application/json").content(patchBody(invalid))).andExpect(status().isBadRequest());
        assertEquals(4,owner.getCharacterId());
    }
    @Test void swaggerExposesProfileAndTeamMemberCharacterFields() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.UserUpdateRequestDto.required",org.hamcrest.Matchers.hasItem("characterId")))
                .andExpect(jsonPath("$.components.schemas.UserUpdateRequestDto.properties.characterId").exists())
                .andExpect(jsonPath("$.components.schemas.UserResponseDto.properties.characterId").exists())
                .andExpect(jsonPath("$.components.schemas.MemberInfo.properties.characterId").exists());
    }
}

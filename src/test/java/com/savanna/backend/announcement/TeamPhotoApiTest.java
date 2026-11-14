package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.TeamRepository;
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
class TeamPhotoApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired TeamRepository teams;
    MockMvc mvc;User owner;
    final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner=new User("photo-owner@example.invalid","photo-owner");owner.updateProfile("팀장","대학교",EnrollmentStatus.ENROLLED);owner.updateCharacter(3);em.persist(owner);em.flush();
    }
    String bearer(){return "Bearer "+tokens.createAccessToken(owner.getUserId());}
    Map<String,Object> request(){return new LinkedHashMap<>(Map.of("name","사진 팀","projectName","프로젝트","projectIntro","소개"));}
    long create(Map<String,Object> values) throws Exception {
        var response=mvc.perform(post("/api/v1/teams").header("Authorization",bearer()).contentType("application/json").content(json.writeValueAsString(values)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("data").get("teamId").asLong();
    }
    @Test void photoUrlPersistsAndIsReturnedByCreateListAndDetail() throws Exception {
        String url="https://images.example.invalid/team.jpg";
        long id=create(request());
        teams.findById(id).orElseThrow().updatePhotoUrl(url);em.flush();em.clear();
        assertEquals(url,teams.findById(id).orElseThrow().getPhotoUrl());
        mvc.perform(get("/api/v1/teams/{id}",id).header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.data.photoUrl").value(url));
        var response=mvc.perform(get("/api/v1/teams").header("Authorization",bearer())).andExpect(status().isOk()).andReturn().getResponse();
        boolean found=false;
        for(var entry:json.readTree(response.getContentAsString()).get("data"))if(entry.get("teamId").asLong()==id){found=true;assertEquals(url,entry.get("photoUrl").asText());}
        assertTrue(found);
    }
    @Test void missingPhotoIsNullAndPatchCanKeepReplaceOrRemovePhoto() throws Exception {
        long id=create(request());
        assertNull(teams.findById(id).orElseThrow().getPhotoUrl());
        teams.findById(id).orElseThrow().updatePhotoUrl("https://images.example.invalid/first.jpg");
        for(String body:new String[]{"{\"name\":\"새 팀 이름\"}","{\"photoUrl\":null}"})
            mvc.perform(patch("/api/v1/teams/{id}",id).header("Authorization",bearer()).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.photoUrl").value("https://images.example.invalid/first.jpg"));
        mvc.perform(patch("/api/v1/teams/{id}",id).header("Authorization",bearer()).contentType("application/json").content("{\"photoUrl\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.photoUrl").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void localPreviewAndEmbeddedDataAreNotDurablePhotoUrls() throws Exception {
        long before=teams.count();
        for(String invalid:new String[]{"https://images.example.invalid/new.jpg","blob:http://localhost:5173/local-id","data:image/png;base64,test","javascript:alert(1)"}){
            var body=request();body.put("photoUrl",invalid);
            mvc.perform(post("/api/v1/teams").header("Authorization",bearer()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        assertEquals(before,teams.count());
    }
    @Test void swaggerDescribesPhotoInCreateUpdateAndAllReadDtos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.TeamCreateRequestDto.properties.photoUrl").exists())
                .andExpect(jsonPath("$.components.schemas.TeamUpdateRequestDto.properties.photoUrl").exists())
                .andExpect(jsonPath("$.components.schemas.TeamCreateResponseDto.properties.photoUrl").exists())
                .andExpect(jsonPath("$.components.schemas.TeamListResponseDto.properties.photoUrl").exists())
                .andExpect(jsonPath("$.components.schemas.TeamDetailResponseDto.properties.photoUrl").exists());
    }
}

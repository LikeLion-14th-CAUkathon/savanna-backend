package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.service.TeamPhotoStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="recommendation.ai.enabled=false")
@Transactional
class TeamPhotoUploadTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean TeamPhotoStorage storage;
    MockMvc mvc; User leader,member,outsider;Team team;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        leader=new User("upload-leader@test.invalid","leader");member=new User("upload-member@test.invalid","member");outsider=new User("upload-outsider@test.invalid","outsider");
        em.persist(leader);em.persist(member);em.persist(outsider);
        team=new Team("team","project","intro",null,null,null);em.persist(team);
        em.persist(new TeamMember(leader,team,TeamRole.LEADER));em.persist(new TeamMember(member,team,TeamRole.MEMBER));em.flush();
        when(storage.readUrl(anyString())).thenReturn("https://private.example.invalid/signed-one");
    }
    String auth(User u){return "Bearer "+jwt.createAccessToken(u.getUserId());}
    MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","untrusted.jpg","text/plain",out.toByteArray());
    }
    @Test void uploadsVerifiedContentStoresOnlyKeyAndSignsEveryRead() throws Exception {
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(image()).header("Authorization",auth(leader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresInSeconds").value(600));
        em.flush();em.clear();
        Team saved=em.find(Team.class,team.getTeamId());
        assertNull(saved.getPhotoUrl());assertTrue(saved.getPhotoObjectKey().matches("team-photos/\\d+/[a-f0-9-]+\\.png"));
        verify(storage).put(eq(saved.getPhotoObjectKey()),any(byte[].class),eq("image/png"));
        when(storage.readUrl(saved.getPhotoObjectKey())).thenReturn("https://private.example.invalid/signed-two");
        mvc.perform(get("/api/v1/teams/{id}",team.getTeamId()).header("Authorization",auth(member)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.photoUrl").value("https://private.example.invalid/signed-two"));
        mvc.perform(get("/api/v1/teams").header("Authorization",auth(leader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].photoUrl").value("https://private.example.invalid/signed-two"));
    }
    @Test void rejectsAnonymousMembersAndOutsidersBeforeS3() throws Exception {
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(image())).andExpect(status().isUnauthorized());
        for(User u:new User[]{member,outsider})
            mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(image()).header("Authorization",auth(u))).andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }
    @Test void rejectsFakeEmptyOversizedAndUnsupportedFiles() throws Exception {
        for(var file:new MockMultipartFile[]{new MockMultipartFile("file","x.png","image/png",new byte[0]),new MockMultipartFile("file","x.png","image/png","fake".getBytes())})
            mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(file).header("Authorization",auth(leader))).andExpect(status().is4xxClientError());
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(new MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1])).header("Authorization",auth(leader))).andExpect(status().isPayloadTooLarge());
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"gif",out);
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(new MockMultipartFile("file","x.gif","image/gif",out.toByteArray())).header("Authorization",auth(leader))).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(storage);
    }
    @Test void storageFailureKeepsPreviousPhotoAndSanitizesError() throws Exception {
        team.updatePhotoObjectKey("team-photos/old.png");
        doThrow(software.amazon.awssdk.core.exception.SdkClientException.create("sensitive upstream data")).when(storage).put(anyString(),any(byte[].class),anyString());
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(image()).header("Authorization",auth(leader)))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
        assertEquals("team-photos/old.png",team.getPhotoObjectKey());
    }
    @Test void clearRemovesKeyAndCannotAssignAnotherTeamsUrl() throws Exception {
        team.updatePhotoObjectKey("team-photos/existing.png");
        mvc.perform(patch("/api/v1/teams/{id}",team.getTeamId()).header("Authorization",auth(leader)).contentType("application/json").content("{\"photoUrl\":\"https://private.example.invalid/another\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/teams/{id}",team.getTeamId()).header("Authorization",auth(leader)).contentType("application/json").content("{\"photoUrl\":\"\"}")).andExpect(status().isOk());
        assertNull(team.getPhotoObjectKey());assertNull(team.getPhotoUrl());
    }
    @Test void rejectsExcessiveDimensionsMissingFileAndUnknownTeam() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6001,1,BufferedImage.TYPE_INT_RGB),"png",out);
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(new MockMultipartFile("file","wide.png","image/png",out.toByteArray())).header("Authorization",auth(leader))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).header("Authorization",auth(leader))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/teams/{id}/photo",Long.MAX_VALUE).file(image()).header("Authorization",auth(leader))).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }
    @Test void jpegUploadReplacesOldPhotoAndSignatureFailureKeepsPreviousKey() throws Exception {
        team.updatePhotoObjectKey("team-photos/old.jpg");
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",out);
        var file=new MockMultipartFile("file","x.jpeg","image/jpeg",out.toByteArray());
        when(storage.readUrl(anyString())).thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("private detail"));
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(file).header("Authorization",auth(leader))).andExpect(status().isServiceUnavailable());
        assertEquals("team-photos/old.jpg",team.getPhotoObjectKey());
        doReturn("https://private.example.invalid/new").when(storage).readUrl(anyString());
        mvc.perform(multipart("/api/v1/teams/{id}/photo",team.getTeamId()).file(file).header("Authorization",auth(leader))).andExpect(status().isOk());
        assertTrue(team.getPhotoObjectKey().endsWith(".jpg"));assertNull(team.getPhotoUrl());
    }
}

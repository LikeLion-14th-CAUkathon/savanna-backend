package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="recommendation.ai.enabled=false") @Transactional
class ProposalDuplicateApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider tokens;
    @Autowired ExpeditionRepository expeditions;
    @Autowired ProposalRepository proposals;
    @Autowired ProposalRecipientRepository recipients;
    MockMvc mvc;
    User sender,member;
    Team team;
    Announcement announcement;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sender=user("duplicate-sender");member=user("duplicate-member");
        team=new Team("팀","프로젝트","소개",null,null,null);em.persist(team);
        em.persist(new TeamMember(sender,team,TeamRole.LEADER));em.persist(new TeamMember(member,team,TeamRole.MEMBER));
        var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        announcement=Announcement.builder().title("중복 제안 테스트").applicationStartsAt(today.minusDays(1).atStartOfDay()).deadline(today.plusDays(7)).build();
        em.persist(announcement);em.flush();
    }
    private User user(String key){var user=new User(key+"@example.invalid",key);em.persist(user);return user;}
    private String path(){return "/api/v1/announcements/"+announcement.getAnnouncementId()+"/proposals";}
    private String body(Team selected,String message){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("teamId",selected.getTeamId(),"message",message));}
    private String bearer(User user){return "Bearer "+tokens.createAccessToken(user.getUserId());}

    @Test void retryAndDifferentSenderInSameTeamBothReturn409WithoutCreatingExtraRows() throws Exception {
        long expeditionBefore=expeditions.count(),proposalBefore=proposals.count(),recipientBefore=recipients.count();
        mvc.perform(post(path()).header("Authorization",bearer(sender)).contentType("application/json").content(body(team,"참여해요")))
                .andExpect(status().isCreated());
        for(var user:new User[]{sender,member}) {
            mvc.perform(post(path()).header("Authorization",bearer(user)).contentType("application/json").content(body(team,"메시지를 바꿔도 중복")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        }
        assertEquals(expeditionBefore+1,expeditions.count());assertEquals(proposalBefore+1,proposals.count());assertEquals(recipientBefore+1,recipients.count());
    }
    @Test void differentTeamCanProposeTheSameAnnouncement() throws Exception {
        var otherSender=user("other-team-sender");
        var otherTeam=new Team("다른 팀","프로젝트","소개",null,null,null);em.persist(otherTeam);
        em.persist(new TeamMember(otherSender,otherTeam,TeamRole.LEADER));em.flush();
        mvc.perform(post(path()).header("Authorization",bearer(sender)).contentType("application/json").content(body(team,"첫 팀 제안")))
                .andExpect(status().isCreated());
        mvc.perform(post(path()).header("Authorization",bearer(otherSender)).contentType("application/json").content(body(otherTeam,"다른 팀 제안")))
                .andExpect(status().isCreated());
        assertTrue(expeditions.existsByTeamIdAndAnnouncement_AnnouncementId(team.getTeamId(),announcement.getAnnouncementId()));
        assertTrue(expeditions.existsByTeamIdAndAnnouncement_AnnouncementId(otherTeam.getTeamId(),announcement.getAnnouncementId()));
    }
    @Test void swaggerDocumentsConflictAsWellAsSuccess() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/announcements/{announcementId}/proposals'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/announcements/{announcementId}/proposals'].post.responses['409'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
    }
}

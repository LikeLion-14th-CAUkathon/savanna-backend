package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class ProposalCharacterApiTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired JwtTokenProvider jwt;
    MockMvc mvc;User sender,receiver,unset;Long proposalId,recipientId,expeditionId;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sender=user("character-sender",2);receiver=user("character-receiver",7);unset=user("character-unset",null);
        var team=new Team("team","project","intro",null,null,null);em.persist(team);
        em.persist(new TeamMember(sender,team,TeamRole.LEADER));em.persist(new TeamMember(receiver,team,TeamRole.MEMBER));em.persist(new TeamMember(unset,team,TeamRole.MEMBER));
        var a=Announcement.builder().title("공고").deadline(LocalDate.now().plusDays(10)).build();em.persist(a);
        var e=new Expedition(team.getTeamId(),a);em.persist(e);var p=new Proposal(e,sender,"함께 해요");em.persist(p);
        var r=new ProposalRecipient(p,receiver);em.persist(r);em.persist(new ProposalRecipient(p,unset));
        em.flush();proposalId=p.getProposalId();recipientId=r.getProposalRecipientId();expeditionId=e.getExpeditionId();em.clear();
    }
    User user(String name,Integer character){var u=new User(name+"@example.invalid",name);u.updateProfile(name,"대학",EnrollmentStatus.ENROLLED);if(character!=null)u.updateCharacter(character);em.persist(u);return u;}
    String auth(User user){return "Bearer "+jwt.createAccessToken(user.getUserId());}
    @Test void listsAndReadResponseReturnSenderAndEachRecipientsOwnCharacter() throws Exception {
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization",auth(receiver)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderId").value(sender.getUserId()))
            .andExpect(jsonPath("$[0].senderCharacterId").value(2));
        mvc.perform(patch("/api/v1/proposal-recipients/{id}/read",recipientId).header("Authorization",auth(receiver)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.senderCharacterId").value(2));
        mvc.perform(get("/api/v1/proposals/sent").header("Authorization",auth(sender)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderCharacterId").value(2));
        mvc.perform(get("/api/v1/proposals/{id}/recipients",proposalId).header("Authorization",auth(sender)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.recipients[0].userId").value(receiver.getUserId())).andExpect(jsonPath("$.recipients[0].characterId").value(7))
            .andExpect(jsonPath("$.recipients[1].userId").value(unset.getUserId())).andExpect(jsonPath("$.recipients[1].characterId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/expeditions/{id}/proposals",expeditionId).header("Authorization",auth(receiver)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderCharacterId").value(2))
            .andExpect(jsonPath("$[0].recipients[0].characterId").value(7)).andExpect(jsonPath("$[0].recipients[1].characterId").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void usesCurrentSavedProfileRatherThanSnapshotOrLoggedInUsersCharacter() throws Exception {
        em.find(User.class,sender.getUserId()).updateCharacter(4);em.find(User.class,receiver.getUserId()).updateCharacter(9);em.flush();em.clear();
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization",auth(receiver)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderCharacterId").value(4));
        mvc.perform(get("/api/v1/proposals/{id}/recipients",proposalId).header("Authorization",auth(sender)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recipients[0].characterId").value(9));
        var previous=em.find(Proposal.class,proposalId);
        var announcement=Announcement.builder().title("다른 공고").deadline(LocalDate.now().plusDays(10)).build();em.persist(announcement);
        var expedition=new Expedition(previous.getExpedition().getTeamId(),announcement);em.persist(expedition);
        var nullSenderProposal=new Proposal(expedition,em.find(User.class,unset.getUserId()),"미설정 발신자");em.persist(nullSenderProposal);em.persist(new ProposalRecipient(nullSenderProposal,em.find(User.class,receiver.getUserId())));em.flush();em.clear();
        mvc.perform(get("/api/v1/proposal-recipients/received").header("Authorization",auth(receiver)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].senderCharacterId").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void swaggerShowsCharacterFieldsOnActualResponseSchemas() throws Exception {
        var result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode root=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        for(String schema:new String[]{"ReceivedProposalResponse","SentProposalResponse","ExpeditionProposalResponse"})
            assertTrue(root.get("components").get("schemas").get(schema).get("properties").has("senderCharacterId"),schema);
        var operation=root.get("paths").get("/api/v1/proposals/{proposalId}/recipients").get("get");
        var schema=resolve(root,operation.get("responses").get("200").get("content").get("application/json").get("schema"));
        var recipient=resolve(root,schema.get("properties").get("recipients").get("items"));assertTrue(recipient.get("properties").has("characterId"));
    }
    JsonNode resolve(JsonNode root,JsonNode node){return node.has("$ref")?root.at(node.get("$ref").asText().substring(1)):node;}
}

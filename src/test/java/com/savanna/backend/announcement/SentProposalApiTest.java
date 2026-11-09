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
import java.time.LocalDateTime;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class SentProposalApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    MockMvc mvc;
    User sender, other;
    Team team;
    int sequence;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sender = user("sender");
        other = user("other");
        team = new Team("팀", "프로젝트", "소개", null, null, null);
        em.persist(team);
        // 같은 팀의 다른 사용자에게도 발신자 전용 정보를 공개하지 않습니다.
        em.persist(new TeamMember(sender, team, TeamRole.LEADER));
        em.persist(new TeamMember(other, team, TeamRole.MEMBER));
        em.flush();
    }

    User user(String suffix) {
        var user = new User("sent-" + suffix + "@example.com", "sent-sub-" + suffix);
        user.updateProfile(suffix, "대학", EnrollmentStatus.ENROLLED);
        em.persist(user);
        return user;
    }

    String bearer(User user) { return "Bearer " + tokens.createAccessToken(user.getUserId()); }
    String path(Proposal proposal) { return "/api/v1/proposals/" + proposal.getProposalId() + "/recipients"; }

    Proposal proposal(User author) {
        var announcement = Announcement.builder().title("공고 " + ++sequence)
                .deadline(LocalDateTime.now().plusDays(10)).build();
        em.persist(announcement);
        var expedition = new Expedition(team.getTeamId(), announcement);
        em.persist(expedition);
        var proposal = new Proposal(expedition, author, "참가해요");
        em.persist(proposal);
        em.flush();
        return proposal;
    }

    ProposalRecipient recipient(Proposal proposal, User user, ProposalRecipientStatus status) {
        var recipient = new ProposalRecipient(proposal, user);
        if (status != ProposalRecipientStatus.PENDING) recipient.respond(status);
        em.persist(recipient);
        em.flush();
        return recipient;
    }

    @Test void listsOnlyOwnProposalsAndBreaksTimestampTiesByIdDescending() throws Exception {
        var first = proposal(sender);
        var latest = proposal(sender);
        proposal(other);
        var sameTime = LocalDateTime.of(2026, 11, 1, 12, 0);
        em.createQuery("update Proposal p set p.createdAt = :time where p.sender = :sender")
                .setParameter("time", sameTime).setParameter("sender", sender).executeUpdate();
        em.clear();
        mvc.perform(get("/api/v1/proposals/sent").header("Authorization", bearer(sender)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].proposalId").value(latest.getProposalId()))
                .andExpect(jsonPath("$[1].proposalId").value(first.getProposalId()))
                .andExpect(jsonPath("$[0].announcementTitle").value("공고 2"))
                .andExpect(jsonPath("$[0].teamId").value(team.getTeamId()))
                .andExpect(jsonPath("$[0].expeditionId").isNumber())
                .andExpect(jsonPath("$[0].announcementId").isNumber())
                .andExpect(jsonPath("$[0].message").value("참가해요"));
    }

    @Test void listsNewestTimestampFirstAndEmptyListForUserWithoutSentProposals() throws Exception {
        var first = proposal(sender);
        var latest = proposal(sender);
        em.createQuery("update Proposal p set p.createdAt = :time where p.proposalId = :id")
                .setParameter("time", LocalDateTime.of(2026, 1, 1, 0, 0))
                .setParameter("id", latest.getProposalId()).executeUpdate();
        em.clear();
        mvc.perform(get("/api/v1/proposals/sent").header("Authorization", bearer(sender)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].proposalId").value(first.getProposalId()));
        mvc.perform(get("/api/v1/proposals/sent").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void returnsCountsAndRecipientDetailsOnlyForSelectedProposal() throws Exception {
        var proposal = proposal(sender);
        var joined = recipient(proposal, other, ProposalRecipientStatus.JOINED);
        recipient(proposal, user("declined"), ProposalRecipientStatus.DECLINED);
        recipient(proposal, user("pending"), ProposalRecipientStatus.PENDING);
        recipient(proposal(sender), other, ProposalRecipientStatus.JOINED);
        em.clear();
        mvc.perform(get(path(proposal)).header("Authorization", bearer(sender)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proposalId").value(proposal.getProposalId()))
                .andExpect(jsonPath("$.totalCount").value(3)).andExpect(jsonPath("$.joinedCount").value(1))
                .andExpect(jsonPath("$.declinedCount").value(1)).andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.recipients.length()").value(3))
                .andExpect(jsonPath("$.recipients[0].proposalRecipientId").value(joined.getProposalRecipientId()))
                .andExpect(jsonPath("$.recipients[0].userId").value(other.getUserId()))
                .andExpect(jsonPath("$.recipients[0].name").value("other"))
                .andExpect(jsonPath("$.recipients[0].status").value("JOINED"))
                .andExpect(jsonPath("$.recipients[0].respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.recipients[2].status").value("PENDING"))
                .andExpect(jsonPath("$.recipients[2].respondedAt").isEmpty());
    }

    @Test void returnsZeroCountsWhenNoRecipients() throws Exception {
        var proposal = proposal(sender);
        mvc.perform(get(path(proposal)).header("Authorization", bearer(sender)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.joinedCount").value(0)).andExpect(jsonPath("$.declinedCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0)).andExpect(jsonPath("$.recipients").isEmpty());
    }

    @Test void rejectsAnonymousNonSenderMissingAndInvalidIds() throws Exception {
        var proposal = proposal(sender);
        recipient(proposal, other, ProposalRecipientStatus.PENDING);
        mvc.perform(get("/api/v1/proposals/sent")).andExpect(status().isUnauthorized());
        mvc.perform(get(path(proposal))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(proposal)).header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/proposals/9223372036854775807/recipients").header("Authorization", bearer(sender)))
                .andExpect(status().isNotFound());
        for (String id : new String[]{"0", "-1", "invalid"}) {
            mvc.perform(get("/api/v1/proposals/" + id + "/recipients").header("Authorization", bearer(sender)))
                    .andExpect(status().isBadRequest());
        }
    }
}

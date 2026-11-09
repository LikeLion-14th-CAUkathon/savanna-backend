package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.error.*;
import com.savanna.backend.service.TeamAccessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;
import com.savanna.backend.service.ProposalService;
import com.savanna.backend.dto.request.ProposalCreateRequest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TeamAccessIntegrationTest {
    @Autowired TeamAccessService access;
    @Autowired EntityManager em;
    @Autowired ProposalService proposals;

    private User user(String suffix, EnrollmentStatus status) {
        var user = new User(suffix + "@team.test", "team-" + suffix);
        user.updateProfile(suffix, status);
        em.persist(user);
        return user;
    }

    private Team team() {
        var team = new Team("팀", "프로젝트", "소개", null, null, null);
        em.persist(team);
        return team;
    }

    private void join(User user, Team team, TeamRole role) {
        em.persist(new TeamMember(user, team, role));
        em.flush();
    }

    private UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(user.getUserId(), null, List.of());
    }

    private void fails(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode());
    }

    @Test void membersCanCollaborateAndProposalTargetsOnlyOtherTeamMembers() {
        var leader = user("leader", EnrollmentStatus.ENROLLED);
        var member = user("member", EnrollmentStatus.ON_LEAVE);
        var team = team();
        join(leader, team, TeamRole.LEADER);
        join(member, team, TeamRole.MEMBER);

        assertEquals(member.getUserId(), access.requireMember(auth(member), team.getTeamId(), true));
        var snapshot = access.read(auth(member), null).orElseThrow();
        assertEquals(team.getTeamId(), snapshot.teamId());
        assertTrue(snapshot.memberEnrollmentStatuses().containsAll(List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ON_LEAVE)));
        assertEquals(2, snapshot.memberEnrollmentStatuses().size());
        var proposal = access.requireTeam(auth(member));
        assertEquals(member.getUserId(), proposal.senderId());
        assertEquals(List.of(leader.getUserId()), proposal.recipientIds());
    }

    @Test void rejectsOutsidersMissingTeamsAndUntrustedPrincipals() {
        var outsider = user("outsider", null);
        var team = team();
        fails(ErrorCode.FORBIDDEN, () -> access.requireMember(auth(outsider), team.getTeamId(), false));
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> access.requireMember(auth(outsider), Long.MAX_VALUE, false));
        fails(ErrorCode.UNAUTHORIZED, () -> access.requireMember(() -> outsider.getUserId().toString(), team.getTeamId(), false));
        fails(ErrorCode.UNAUTHORIZED, () -> access.requireTeam(null));
        assertTrue(access.read(auth(outsider), null).isEmpty());
    }

    @Test void multipleTeamsRequireExplicitSelectionAndUnknownEnrollmentIsPreserved() {
        var user = user("multi", null);
        var first = team();
        var second = team();
        join(user, first, TeamRole.LEADER);
        join(user, second, TeamRole.MEMBER);
        assertTrue(access.read(auth(user), null).isEmpty());
        fails(ErrorCode.INVALID_REQUEST, () -> access.requireTeam(auth(user)));
        assertEquals(second.getTeamId(), access.requireTeam(auth(user), second.getTeamId()).teamId());
        var snapshot = access.read(auth(user), first.getTeamId()).orElseThrow();
        assertEquals(1, snapshot.memberEnrollmentStatuses().size());
        assertNull(snapshot.memberEnrollmentStatuses().get(0));
    }

    @Test void proposalPersistsRealTeamAndRecipientsAndRejectsDuplicatesAndOtherTeams() {
        var sender = user("sender", EnrollmentStatus.ENROLLED);
        var recipient = user("recipient", EnrollmentStatus.ENROLLED);
        var outsider = user("proposal-outsider", EnrollmentStatus.ENROLLED);
        var team = team();
        join(sender, team, TeamRole.LEADER);
        join(recipient, team, TeamRole.MEMBER);
        var announcement = Announcement.builder().title("공고")
                .applicationStartsAt(LocalDateTime.now().minusDays(1))
                .deadline(LocalDateTime.now().plusDays(10)).build();
        em.persist(announcement);
        em.flush();
        var request = new ProposalCreateRequest("참가해요", team.getTeamId());
        fails(ErrorCode.FORBIDDEN, () -> proposals.createProposal(announcement.getAnnouncementId(), request, auth(outsider)));
        proposals.createProposal(announcement.getAnnouncementId(), request, auth(sender));
        em.flush();
        var saved = em.createQuery("select p from Proposal p where p.sender = :sender", Proposal.class)
                .setParameter("sender", sender).getSingleResult();
        assertEquals(team.getTeamId(), saved.getExpedition().getTeamId());
        var recipients = em.createQuery("select r from ProposalRecipient r where r.proposal = :proposal", ProposalRecipient.class)
                .setParameter("proposal", saved).getResultList();
        assertEquals(1, recipients.size());
        assertEquals(recipient.getUserId(), recipients.get(0).getUser().getUserId());
        assertEquals(ProposalRecipientStatus.PENDING, recipients.get(0).getStatus());
        fails(ErrorCode.CONFLICT, () -> proposals.createProposal(announcement.getAnnouncementId(), request, auth(sender)));
    }
}

package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.*;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.TeamInvitationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TeamInvitationMergeRegressionTest {
    @Autowired EntityManager em;
    @Autowired TeamInvitationService invitations;
    @Autowired TeamInvitationRepository invitationRepository;
    @Autowired TeamMemberRepository members;
    Team team;
    User leader, newcomer;

    @BeforeEach void setup() {
        leader = new User("merge-leader@example.com", "merge-leader");
        newcomer = new User("merge-newcomer@example.com", "merge-newcomer");
        leader.updateProfile("팀장", "대학", EnrollmentStatus.ENROLLED);
        newcomer.updateProfile("새 팀원", "대학", EnrollmentStatus.ENROLLED);
        em.persist(leader); em.persist(newcomer);
        team = new Team("병합 검증 팀", "프로젝트", "소개", null, null, null);
        em.persist(team);
        em.persist(new TeamMember(leader, team, TeamRole.LEADER));
        em.flush();
    }

    @Test void permanentInvitationIsReusedAndNewMemberCanAccept() {
        var first = invitations.createInvitation(leader.getUserId(), team.getTeamId());
        var again = invitations.createInvitation(leader.getUserId(), team.getTeamId());
        assertEquals(first.invitationId(), again.invitationId());
        assertEquals(first.token(), again.token());
        assertNull(first.expiresAt());
        assertEquals(1, invitationRepository.findAllByTeam_TeamId(team.getTeamId()).size());
        assertEquals(team.getTeamId(), invitations.previewInvitation(first.token()).teamId());
        assertEquals(team.getTeamId(), invitations.acceptInvitation(newcomer.getUserId(), first.token()).teamId());
        em.flush(); em.clear();
        assertEquals(TeamRole.MEMBER, members.findByUser_UserIdAndTeam_TeamId(newcomer.getUserId(), team.getTeamId()).orElseThrow().getRole());
        assertEquals(TeamRole.LEADER, members.findByUser_UserIdAndTeam_TeamId(leader.getUserId(), team.getTeamId()).orElseThrow().getRole());
    }

    @Test void ordinaryMemberCannotCreateInvitation() {
        em.persist(new TeamMember(newcomer, team, TeamRole.MEMBER)); em.flush();
        var exception = assertThrows(ResponseStatusException.class,
                () -> invitations.createInvitation(newcomer.getUserId(), team.getTeamId()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(invitationRepository.findAllByTeam_TeamId(team.getTeamId()).isEmpty());
    }
}

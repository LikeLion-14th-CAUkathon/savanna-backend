package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TeamDeletionAtomicityTest {
    @Autowired TeamService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamInvitationRepository invitations;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @MockitoSpyBean TeamDeletionService deletion;

    @Test void failureAfterDeletionRollsBackTeamMembershipAndAllHistory() {
        TeamDeletionService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(deletion);
        var tx=new TransactionTemplate(transactions);
        var u=new User(UUID.randomUUID()+"@test.invalid","rollback");
        u.updateProfile("name","uni",EnrollmentStatus.ENROLLED);u.updateCharacter(1);users.saveAndFlush(u);
        Long teamId=service.createTeam(u.getUserId(),new TeamCreateRequestDto("team","project","intro",null,null,null,null)).teamId();
        Long[] ids=tx.execute(s->{
            var a=Announcement.builder().title("rollback").deadline(LocalDate.now().plusDays(10)).build();em.persist(a);
            var e=new Expedition(teamId,a);em.persist(e);
            var p=new Proposal(e,em.find(User.class,u.getUserId()),"test");em.persist(p);
            var r=new ProposalRecipient(p,em.find(User.class,u.getUserId()));em.persist(r);em.flush();
            return new Long[]{a.getAnnouncementId(),e.getExpeditionId(),p.getProposalId(),r.getProposalRecipientId()};
        });
        try {
            doAnswer(call->{call.callRealMethod();throw new IllegalStateException("simulated failure after deletion");}).when(target).deleteTeam(teamId);
            assertThrows(IllegalStateException.class,()->service.leaveTeam(u.getUserId(),teamId));
            tx.executeWithoutResult(s->{
                assertTrue(teams.existsById(teamId));assertEquals(1,members.countByTeam_TeamId(teamId));
                assertEquals(1,invitations.findAllByTeam_TeamId(teamId).size());
                assertNotNull(em.find(Expedition.class,ids[1]));assertNotNull(em.find(Proposal.class,ids[2]));
                assertNotNull(em.find(ProposalRecipient.class,ids[3]));
            });
        } finally {
            reset(target);service.leaveTeam(u.getUserId(),teamId);
            tx.executeWithoutResult(s->em.remove(em.find(Announcement.class,ids[0])));users.deleteById(u.getUserId());
        }
    }
}

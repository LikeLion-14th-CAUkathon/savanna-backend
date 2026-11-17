package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.dto.request.ProposalCreateRequest;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.ProposalRepository;
import com.savanna.backend.service.ProposalService;
import com.savanna.backend.service.TeamDeletionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProposalLimitConcurrencyTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ProposalService service;
    @Autowired ProposalRepository proposals;
    @Autowired TeamDeletionService deletion;

    @Test void simultaneousDifferentAnnouncementsCannotExceedSixAcrossSenders() throws Exception {
        var tx = new TransactionTemplate(transactions);
        record Fixture(Long teamId, List<Long> userIds, List<Long> announcementIds) {}
        var fixture = tx.execute(s -> {
            String key = UUID.randomUUID().toString();
            var first = new User(key + "-first@test.invalid", key + "-first");
            var second = new User(key + "-second@test.invalid", key + "-second");
            em.persist(first); em.persist(second);
            var team = new Team("동시 제안", "프로젝트", "소개", null, null, null);
            em.persist(team);
            em.persist(new TeamMember(first, team, TeamRole.LEADER));
            em.persist(new TeamMember(second, team, TeamRole.MEMBER));
            var ids = new java.util.ArrayList<Long>();
            var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            for (int i = 0; i < 7; i++) {
                var announcement = Announcement.builder().title(key + i)
                        .applicationStartsAt(today.minusDays(1).atStartOfDay()).deadline(today.plusDays(7)).build();
                em.persist(announcement);
                ids.add(announcement.getAnnouncementId());
                if (i < 5) {
                    var expedition = new Expedition(team.getTeamId(), announcement);
                    em.persist(expedition);
                    em.persist(new Proposal(expedition, first, "기존 제안"));
                }
            }
            em.flush();
            return new Fixture(team.getTeamId(), List.of(first.getUserId(), second.getUserId()), ids);
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    var principal = new UsernamePasswordAuthenticationToken(fixture.userIds().get(index), null, List.of());
                    try {
                        service.createProposal(fixture.announcementIds().get(5 + index),
                                new ProposalCreateRequest("동시 요청", fixture.teamId()), principal);
                        return true;
                    } catch (BusinessException ex) {
                        assertEquals(ErrorCode.PROPOSAL_LIMIT_EXCEEDED, ex.getErrorCode());
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            tx.executeWithoutResult(s -> {
                assertEquals(6, proposals.countByExpedition_TeamId(fixture.teamId()));
                assertEquals(6L, em.createQuery("select count(e) from Expedition e where e.teamId = :id", Long.class)
                        .setParameter("id", fixture.teamId()).getSingleResult());
                assertEquals(1L, em.createQuery("select count(r) from ProposalRecipient r where r.proposal.expedition.teamId = :id", Long.class)
                        .setParameter("id", fixture.teamId()).getSingleResult());
            });
        } finally {
            start.countDown(); pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            tx.executeWithoutResult(s -> {
                deletion.deleteTeam(fixture.teamId());
                for (Long id : fixture.announcementIds()) em.remove(em.find(Announcement.class, id));
                for (Long id : fixture.userIds()) em.remove(em.find(User.class, id));
            });
        }
    }
}

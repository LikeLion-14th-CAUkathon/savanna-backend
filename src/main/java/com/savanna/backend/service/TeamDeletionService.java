package com.savanna.backend.service;

import com.savanna.backend.domain.Expedition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called after acquiring the team write lock and checking the last membership. */
@Service
@RequiredArgsConstructor
public class TeamDeletionService {
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteTeam(Long teamId) {
        em.flush();
        // Wait for expedition writers before deleting their children.
        em.createQuery("select e from Expedition e where e.teamId = :teamId order by e.expeditionId", Expedition.class)
                .setParameter("teamId", teamId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        delete("delete from ProposalRecipient r where r.proposal.proposalId in (select p.proposalId from Proposal p where p.expedition.teamId = :teamId)", teamId);
        delete("delete from Proposal p where p.expedition.expeditionId in (select e.expeditionId from Expedition e where e.teamId = :teamId)", teamId);
        for (String entity : new String[]{"ExpeditionTask", "ExpeditionChecklist", "ExpeditionStageHistory"}) {
            delete("delete from " + entity + " c where c.expedition.expeditionId in (select e.expeditionId from Expedition e where e.teamId = :teamId)", teamId);
        }
        delete("delete from Expedition e where e.teamId = :teamId", teamId);
        delete("delete from TeamInvitation i where i.team.teamId = :teamId", teamId);
        delete("delete from AnnouncementRecommendation r where r.team.teamId = :teamId", teamId);
        delete("delete from TeamMember m where m.team.teamId = :teamId", teamId);
        delete("delete from Team t where t.teamId = :teamId", teamId);
        // Bulk DML bypasses managed snapshots.
        em.clear();
    }

    private void delete(String jpql, Long teamId) {
        em.createQuery(jpql).setParameter("teamId", teamId).executeUpdate();
    }
}

package com.savanna.backend.repository;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.enums.ProposalRecipientStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProposalRecipientRepository extends JpaRepository<ProposalRecipient, Long> {
    long countByUser_UserIdAndStatus(Long userId, ProposalRecipientStatus status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProposalRecipient r where r.proposalRecipientId = :id")
    java.util.Optional<ProposalRecipient> findForResponse(@Param("id") Long id);

    interface ExpeditionJoinedCount {
        Long getExpeditionId();
        Long getJoinedCount();
    }

    @Query("""
            select r.proposal.expedition.expeditionId as expeditionId,
                   count(distinct r.user.userId) as joinedCount
            from ProposalRecipient r
            where r.proposal.expedition.teamId = :teamId and r.status = :status
            group by r.proposal.expedition.expeditionId
            """)
    List<ExpeditionJoinedCount> countDistinctUsersByTeamAndStatus(
            @Param("teamId") Long teamId, @Param("status") ProposalRecipientStatus status);

    @EntityGraph(attributePaths = {
            "proposal",
            "proposal.sender",
            "proposal.expedition",
            "proposal.expedition.announcement"
    })
    @Query("""
        select r
        from ProposalRecipient r
        where r.user.userId = :userId
        order by r.proposal.createdAt desc,
                 r.proposalRecipientId desc
        """)
    List<ProposalRecipient> findReceivedProposals(
            @Param("userId") Long userId
    );
}

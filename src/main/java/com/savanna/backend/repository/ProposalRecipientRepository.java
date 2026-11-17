package com.savanna.backend.repository;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.enums.ProposalRecipientStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProposalRecipientRepository extends JpaRepository<ProposalRecipient, Long> {
    @EntityGraph(attributePaths = {"user", "proposal"})
    @Query("""
    select r
    from ProposalRecipient r
    where r.proposal.expedition.expeditionId = :expeditionId
      and r.revokedAt is null
      and exists (
          select m
          from TeamMember m
          where m.user.userId = r.user.userId
            and m.team.teamId = r.proposal.expedition.teamId
      )
    order by r.proposalRecipientId asc
    """)
    List<ProposalRecipient> findAllByProposal_Expedition_ExpeditionIdOrderByProposalRecipientIdAsc(
            @Param("expeditionId") Long expeditionId
    );

    @EntityGraph(attributePaths = "user")
    @Query("""
    select r
    from ProposalRecipient r
    where r.proposal.proposalId = :proposalId
      and r.revokedAt is null
      and exists (
          select m
          from TeamMember m
          where m.user.userId = r.user.userId
            and m.team.teamId = r.proposal.expedition.teamId
      )
    order by r.proposalRecipientId asc
    """)
    List<ProposalRecipient> findAllByProposal_ProposalIdOrderByProposalRecipientIdAsc(
            @Param("proposalId") Long proposalId
    );

    @Query("""
        select count(r)
        from ProposalRecipient r
        where r.user.userId = :userId
          and r.status = :status
          and r.revokedAt is null
          and exists (
              select m
              from TeamMember m
              where m.user.userId = :userId
                and m.team.teamId = r.proposal.expedition.teamId
          )
        """)
    long countByUserAndCurrentTeamAndStatus(
            @Param("userId") Long userId,
            @Param("status") ProposalRecipientStatus status
    );

    @Query("""
        select count(r)
        from ProposalRecipient r
        where r.user.userId = :userId
          and r.readAt is null
          and r.revokedAt is null
          and exists (
              select m
              from TeamMember m
              where m.user.userId = :userId
                and m.team.teamId = r.proposal.expedition.teamId
          )
        """)
    long countUnreadProposalsInCurrentTeam(@Param("userId") Long userId);

    @Query("""
        select count(r)
        from ProposalRecipient r
        where r.proposal.sender.userId = :userId
          and r.revokedAt is null
          and r.user.userId <> :userId
          and r.status <> com.savanna.backend.enums.ProposalRecipientStatus.PENDING
          and (case when r.responseVersion = 0 then 1 else r.responseVersion end) > r.responseReadVersion
          and exists (
              select m
              from TeamMember m
              where m.user.userId = :userId
                and m.team.teamId = r.proposal.expedition.teamId
          )
          and exists (
              select m
              from TeamMember m
              where m.user.userId = r.user.userId
                and m.team.teamId = r.proposal.expedition.teamId
          )
        """)
    long countUnreadResponses(@Param("userId") Long userId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProposalRecipient r where r.proposalRecipientId = :id")
    java.util.Optional<ProposalRecipient> findForResponse(@Param("id") Long id);

    interface ExpeditionJoinedCount {
        Long getExpeditionId();
        Long getJoinedCount();
    }

    @Query("""
            select count(distinct r.user.userId) from ProposalRecipient r
            where r.proposal.expedition.expeditionId = :expeditionId
              and r.proposal.expedition.teamId = :teamId
              and r.status = com.savanna.backend.enums.ProposalRecipientStatus.JOINED
              and r.revokedAt is null
              and (r.proposal.sender is null or r.user.userId <> r.proposal.sender.userId)
              and exists (select m from TeamMember m where m.team.teamId = :teamId and m.user.userId = r.user.userId)
            """)
    long countJoinedForExpedition(@Param("expeditionId") Long expeditionId, @Param("teamId") Long teamId);

    @Query("""
            select r.proposal.expedition.expeditionId as expeditionId,
                   count(distinct r.user.userId) as joinedCount
            from ProposalRecipient r
            where r.proposal.expedition.teamId = :teamId and r.status = :status
              and r.revokedAt is null
              and (r.proposal.sender is null or r.user.userId <> r.proposal.sender.userId)
              and exists (select m from TeamMember m where m.team.teamId = :teamId and m.user.userId = r.user.userId)
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
          and r.revokedAt is null
          and exists (
              select m
              from TeamMember m
              where m.user.userId = :userId
                and m.team.teamId = r.proposal.expedition.teamId
          )
        order by r.proposal.createdAt desc,
                 r.proposalRecipientId desc
        """)
    List<ProposalRecipient> findReceivedProposals(
            @Param("userId") Long userId
    );

    boolean existsByProposal_ProposalIdAndUser_UserId(
            Long proposalId,
            Long userId
    );

    // 이전 탈퇴 정책으로 남은 무효 기록만 정리합니다. 유효한 수신 기록은 유지합니다.
    @Modifying(flushAutomatically = true)
    @Query("""
        delete from ProposalRecipient r
        where r.user.userId = :userId
          and r.revokedAt is not null
          and r.proposal.proposalId in (
              select p.proposalId from Proposal p
              where p.expedition.teamId = :teamId
          )
        """)
    int deleteRevokedByUserAndTeam(@Param("userId") Long userId, @Param("teamId") Long teamId);

    @Modifying(flushAutomatically = true)
    @Query("""
        delete from ProposalRecipient r
        where r.user.userId = :userId
            and r.status = :status
            and r.proposal.proposalId in (
                select p.proposalId
                from Proposal p
                where p.expedition.teamId = :teamId
        )
      """)
    int deletePendingByUserAndTeam(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId,
            @Param("status") ProposalRecipientStatus status
    );

    @Query("""
        select r
        from ProposalRecipient r
        where r.user.userId = :userId
          and r.proposal.expedition.teamId = :teamId
          and r.revokedAt is null
        """)
    List<ProposalRecipient> findUnrevokedByUserAndTeam(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        delete from ProposalRecipient r
        where r.user.userId = :userId
          and r.proposal.proposalId in (
              select p.proposalId
              from Proposal p
              where p.expedition.teamId = :teamId
          )
        """)
    int deleteAllByUserAndTeam(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId
    );
}

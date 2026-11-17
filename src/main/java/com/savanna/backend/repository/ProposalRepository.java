package com.savanna.backend.repository;

import com.savanna.backend.domain.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long> {
    long countByExpedition_TeamId(Long teamId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "sender")
    java.util.List<Proposal> findAllByExpedition_ExpeditionIdOrderByCreatedAtDescProposalIdDesc(Long expeditionId);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"expedition", "expedition.announcement", "sender"}
    )
    @Query("""
    select p
    from Proposal p
    where p.sender.userId = :userId
      and exists (
          select m
          from TeamMember m
          where m.user.userId = :userId
            and m.team.teamId = p.expedition.teamId
      )
    order by p.createdAt desc, p.proposalId desc
    """)
    List<Proposal> findAllBySender_UserIdOrderByCreatedAtDescProposalIdDesc(
            @Param("userId") Long userId
    );

    @Query("""
    SELECT p
    FROM Proposal p
    JOIN p.expedition e
    JOIN e.announcement a
    WHERE e.teamId = :teamId
      AND (a.deadline IS NULL OR a.deadline >= :today)
      AND e.submissionCompletedAt IS NULL
    """)
    List<Proposal> findActiveProposalsByTeamId(
            @Param("teamId") Long teamId,
            @Param("today") LocalDate today
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Proposal p
        set p.sender = null
        where p.sender.userId = :userId
          and p.expedition.teamId = :teamId
        """)
    int detachSenderByUserAndTeam(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId
    );

}

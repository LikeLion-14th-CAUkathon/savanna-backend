package com.savanna.backend.repository;

import com.savanna.backend.domain.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "sender")
    java.util.List<Proposal> findAllByExpedition_ExpeditionIdOrderByCreatedAtDescProposalIdDesc(Long expeditionId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"expedition", "expedition.announcement", "sender"})
    java.util.List<Proposal> findAllBySender_UserIdOrderByCreatedAtDescProposalIdDesc(Long userId);

    @Query("""
    SELECT p
    FROM Proposal p
    JOIN p.expedition e
    JOIN e.announcement a
    WHERE e.teamId = :teamId
      AND a.deadline >= :today
      AND e.submissionCompletedAt IS NULL
    """)
    List<Proposal> findActiveProposalsByTeamId(
            @Param("teamId") Long teamId,
            @Param("today") LocalDate today
    );
}

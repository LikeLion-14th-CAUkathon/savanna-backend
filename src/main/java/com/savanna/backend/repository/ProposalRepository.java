package com.savanna.backend.repository;

import com.savanna.backend.domain.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "sender")
    java.util.List<Proposal> findAllByExpedition_ExpeditionIdOrderByCreatedAtDescProposalIdDesc(Long expeditionId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"expedition", "expedition.announcement"})
    java.util.List<Proposal> findAllBySender_UserIdOrderByCreatedAtDescProposalIdDesc(Long userId);
}

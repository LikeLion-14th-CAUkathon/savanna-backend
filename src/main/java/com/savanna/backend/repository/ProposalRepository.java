package com.savanna.backend.repository;

import com.savanna.backend.domain.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long> {
}
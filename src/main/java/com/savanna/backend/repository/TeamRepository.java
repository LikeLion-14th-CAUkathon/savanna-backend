package com.savanna.backend.repository;

import com.savanna.backend.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository
        extends JpaRepository<Team, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Team t where t.teamId = :id")
    java.util.Optional<Team> findForRecommendationUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Team t where t.teamId = :id")
    java.util.Optional<Team> findForProposalResponse(@org.springframework.data.repository.query.Param("id") Long id);
}

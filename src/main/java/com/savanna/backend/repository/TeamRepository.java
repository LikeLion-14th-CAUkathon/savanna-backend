package com.savanna.backend.repository;

import com.savanna.backend.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository
        extends JpaRepository<Team, Long> {
}

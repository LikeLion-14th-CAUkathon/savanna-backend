package com.savanna.backend.repository;

import com.savanna.backend.domain.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository
        extends JpaRepository<TeamInvitation, Long> {

    Optional<TeamInvitation> findByTokenHash(
            String tokenHash
    );

    List<TeamInvitation> findAllByTeam_TeamId(
            Long teamId
    );

    List<TeamInvitation> findAllByTeam_TeamIdOrderByCreatedAtDesc(Long teamId);
}

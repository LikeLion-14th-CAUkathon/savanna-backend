package com.savanna.backend.repository;

import com.savanna.backend.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import java.util.List;

public interface TeamMemberRepository
        extends JpaRepository<TeamMember, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    List<TeamMember> findAllByUser_UserId(Long userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    List<TeamMember> findAllByTeam_TeamId(Long teamId);

    boolean existsByUser_UserIdAndTeam_TeamId(
            Long userId,
            Long teamId
    );

    long countByTeam_TeamId(Long teamId);

    Optional<TeamMember> findByUser_UserIdAndTeam_TeamId(
            Long userId,
            Long teamId
    );

}

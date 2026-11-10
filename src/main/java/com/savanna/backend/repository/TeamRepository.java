package com.savanna.backend.repository;

import com.savanna.backend.domain.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    // 참여 제안 응답 처리 시 팀 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.teamId = :id")
    Optional<Team> findForProposalResponse(@Param("id") Long id);

    // 영구 초대 링크 생성 시 팀 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.teamId = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);
}

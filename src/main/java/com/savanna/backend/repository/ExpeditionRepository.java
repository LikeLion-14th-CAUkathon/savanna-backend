package com.savanna.backend.repository;

import com.savanna.backend.domain.Expedition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ExpeditionRepository extends JpaRepository<Expedition, Long> {
    @EntityGraph(attributePaths = "announcement")
    Optional<Expedition> findByExpeditionId(Long expeditionId);

    Optional<Expedition> findByTeamIdAndAnnouncement_AnnouncementId(Long teamId, Long announcementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expedition e join fetch e.announcement where e.expeditionId = :id")
    Optional<Expedition> findForUpdate(@Param("id") Long id);
}

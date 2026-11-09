package com.savanna.backend.repository;

import com.savanna.backend.domain.ExpeditionChecklist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ExpeditionChecklistRepository extends JpaRepository<ExpeditionChecklist, Long> {
    List<ExpeditionChecklist> findByExpedition_ExpeditionIdOrderBySortOrderAscChecklistIdAsc(Long expeditionId);

    @Query("select max(c.sortOrder) from ExpeditionChecklist c where c.expedition.expeditionId = :id")
    Integer findMaxSortOrder(@Param("id") Long expeditionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExpeditionChecklist c join fetch c.expedition where c.checklistId = :id")
    Optional<ExpeditionChecklist> findForUpdate(@Param("id") Long checklistId);
}

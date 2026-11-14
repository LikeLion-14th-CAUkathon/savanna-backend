package com.savanna.backend.repository;

import com.savanna.backend.domain.ExpeditionTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ExpeditionTaskRepository extends JpaRepository<ExpeditionTask, Long> {
    @Query("""
            select t from ExpeditionTask t where t.expedition.expeditionId = :expeditionId
            order by t.dueDate asc, case when t.dueTime is null then 1 else 0 end asc,
                     t.dueTime asc, t.taskId asc
            """)
    List<ExpeditionTask> findOrderedByExpeditionId(@Param("expeditionId") Long expeditionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ExpeditionTask t join fetch t.expedition where t.taskId = :taskId")
    Optional<ExpeditionTask> findForUpdate(@Param("taskId") Long taskId);
}

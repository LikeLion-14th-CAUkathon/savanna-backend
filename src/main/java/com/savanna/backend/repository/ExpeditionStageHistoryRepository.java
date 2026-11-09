package com.savanna.backend.repository;

import com.savanna.backend.domain.ExpeditionStageHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpeditionStageHistoryRepository extends JpaRepository<ExpeditionStageHistory, Long> {
    Page<ExpeditionStageHistory> findByExpedition_ExpeditionId(Long expeditionId, Pageable pageable);
}

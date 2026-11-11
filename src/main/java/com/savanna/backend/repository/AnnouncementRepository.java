package com.savanna.backend.repository;

import com.savanna.backend.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, Long>,
        JpaSpecificationExecutor<Announcement> {

    Optional<Announcement> findBySourceProviderAndExternalId(String sourceProvider, String externalId);

    java.util.List<Announcement> findByVisibilityTrueAndDeadlineGreaterThanEqualAndApplicationStartsAtLessThanEqualOrderByDeadlineAscAnnouncementIdAsc(
            java.time.LocalDate today, java.time.LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Announcement> findByAnnouncementIdAndVisibilityTrue(Long announcementId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from Announcement a
        where a.announcementId = :announcementId
        """)
    Optional<Announcement> findForProposal(
            @Param("announcementId") Long announcementId
    );
}

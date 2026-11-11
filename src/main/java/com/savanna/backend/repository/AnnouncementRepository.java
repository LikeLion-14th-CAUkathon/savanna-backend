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

    @Query("select a from Announcement a where a.visibility=true and a.excludeReason is null and a.classificationIssue is null and a.deadline>=:today and a.applicationStartsAt<=:now order by a.deadline,a.announcementId")
    java.util.List<Announcement> findByVisibilityTrueAndDeadlineGreaterThanEqualAndApplicationStartsAtLessThanEqualOrderByDeadlineAscAnnouncementIdAsc(
            @Param("today") java.time.LocalDate today, @Param("now") java.time.LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Query("select a from Announcement a where a.announcementId=:id and a.visibility=true and a.excludeReason is null and a.classificationIssue is null")
    java.util.Optional<Announcement> findByAnnouncementIdAndVisibilityTrue(@Param("id") Long announcementId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from Announcement a
        where a.announcementId = :announcementId
        """)
    Optional<Announcement> findForProposal(
            @Param("announcementId") Long announcementId
    );
}

package com.savanna.backend.repository;

import com.savanna.backend.domain.AnnouncementRecommendation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface AnnouncementRecommendationRepository extends JpaRepository<AnnouncementRecommendation, Long> {
    @EntityGraph(attributePaths = "announcement")
    @Query("""
            select r from AnnouncementRecommendation r where r.team.teamId = :teamId
            and r.announcement.visibility = true and r.announcement.deadline >= :today
            and r.announcement.excludeReason is null and r.announcement.classificationIssue is null
            order by r.score desc, r.announcement.deadline asc, r.announcement.announcementId asc
            """)
    List<AnnouncementRecommendation> findVisible(@Param("teamId") Long teamId, @Param("today") LocalDate today);

    @Modifying(flushAutomatically = true)
    @Query("delete from AnnouncementRecommendation r where r.team.teamId = :teamId")
    void deleteForTeam(@Param("teamId") Long teamId);
}

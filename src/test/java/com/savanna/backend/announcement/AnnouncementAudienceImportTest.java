package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.*;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.AnnouncementAudiencePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="recommendation.ai.enabled=false") @Transactional
class AnnouncementAudienceImportTest {
    @Autowired ContestKoreaWriter api;
    @Autowired ContestKoreaCsvWriter csv;
    @Autowired AnnouncementRepository announcements;
    @Autowired AnnouncementSourceEventRepository events;
    private ContestKoreaItem item(String id,String target){return new ContestKoreaItem(id,"아이디어•건축•창업","공모전","주최",target,"11.01~12.31","https://www.contestkorea.com/view?str_no="+id,null,"온라인");}

    @Test void apiExcludesYouthFromListDetailAndRecommendationCandidatesWithoutDeletingAndDoesNotDuplicateAudit() {
        var result=api.save(ContestKoreaCategory.IDEA,List.of(item("youth-only","초등학생, 중학생, 고등학생"),item("mixed-adult","고등학생, 대학생")));
        assertEquals(1,result.excluded());
        var youth=announcements.findBySourceProviderAndExternalId("CONTEST_KOREA","youth-only").orElseThrow();
        var mixed=announcements.findBySourceProviderAndExternalId("CONTEST_KOREA","mixed-adult").orElseThrow();
        assertFalse(youth.isAvailable());assertTrue(mixed.isAvailable());assertNotNull(youth.getExcludeReason());
        assertTrue(announcements.findByAnnouncementIdAndVisibilityTrue(youth.getAnnouncementId()).isEmpty());
        var now=LocalDateTime.of(2026,11,14,12,0);
        var listed=announcements.findAll(AnnouncementSpecification.search(null,null,null,now));
        assertFalse(listed.stream().anyMatch(a->a.getAnnouncementId().equals(youth.getAnnouncementId())));
        assertTrue(listed.stream().anyMatch(a->a.getAnnouncementId().equals(mixed.getAnnouncementId())));
        var candidates=announcements.findByVisibilityTrueAndDeadlineGreaterThanEqualAndApplicationStartsAtLessThanEqualOrderByDeadlineAscAnnouncementIdAsc(now.toLocalDate(),now,PageRequest.of(0,200));
        assertFalse(candidates.stream().anyMatch(a->a.getAnnouncementId().equals(youth.getAnnouncementId())));
        long auditCount=events.findAll().stream().filter(e->"AUDIENCE_EXCLUSION".equals(e.getEventType())).count();
        api.save(ContestKoreaCategory.IDEA,List.of(item("youth-only","초등학생, 중학생, 고등학생")));
        assertEquals(auditCount,events.findAll().stream().filter(e->"AUDIENCE_EXCLUSION".equals(e.getEventType())).count());
        assertTrue(announcements.findById(youth.getAnnouncementId()).isPresent());
    }
    @Test void csvEnrichmentCanExcludeAndPreservesAllExclusionReasonsAcrossRefresh() {
        api.save(ContestKoreaCategory.IDEA,List.of(item("csv-youth","누구나")));
        var row=new HashMap<>(Map.of("api_id","csv-youth","category_source","아이디어•건축•창업","target_raw","청소년","exclude_reason","수동 검토 사유"));
        assertEquals(1,csv.apply(List.of(row),"fixture").excluded());
        var saved=announcements.findBySourceProviderAndExternalId("CONTEST_KOREA","csv-youth").orElseThrow();
        csv.apply(List.of(row),"fixture");
        api.save(ContestKoreaCategory.IDEA,List.of(item("csv-youth","누구나")));
        assertFalse(saved.isAvailable());assertTrue(saved.getExcludeReason().contains("수동 검토 사유"));
        assertEquals(1,Arrays.stream(saved.getExcludeReason().split("\\n")).filter(AnnouncementAudiencePolicy.EXCLUDE_REASON::equals).count());
    }
    @Test void explicitAdultCorrectionRemovesOnlyThisPolicyAndPreservesManualHiddenState() {
        api.save(ContestKoreaCategory.IDEA,List.of(item("corrected-youth","청소년")));
        var saved=announcements.findBySourceProviderAndExternalId("CONTEST_KOREA","corrected-youth").orElseThrow();
        assertFalse(saved.isAvailable());
        csv.apply(List.of(Map.of("api_id","corrected-youth","category_source","아이디어•건축•창업","target_raw","고등학생, 대학생")),"fixture");
        assertTrue(saved.isAvailable());assertNull(saved.getExcludeReason());
        saved.changeVisibility(false);
        csv.apply(List.of(Map.of("api_id","corrected-youth","category_source","아이디어•건축•창업","target_raw","청소년")),"fixture");
        csv.apply(List.of(Map.of("api_id","corrected-youth","category_source","아이디어•건축•창업","target_raw","대학생")),"fixture");
        assertFalse(saved.isAvailable());assertNull(saved.getExcludeReason());assertFalse(saved.isVisibility());
    }
}

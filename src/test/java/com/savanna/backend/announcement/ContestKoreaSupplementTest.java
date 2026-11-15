package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.*;
import com.savanna.backend.repository.AnnouncementRepository;
import com.savanna.backend.enums.AnnouncementField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class ContestKoreaSupplementTest {
    @Autowired ContestKoreaWriter api;
    @Autowired ContestKoreaCsvWriter csv;
    @Autowired AnnouncementRepository repository;
    private ContestKoreaItem item(String id,String category){return new ContestKoreaItem(id,category,"제목","주최","해당자 ▶","11.01~12.31","https://www.contestkorea.com/view?str_no="+id,null,"온라인");}
    private Map<String,String> row(String id){return new HashMap<>(Map.of("api_id",id,"title","CSV 제목","organizer","주최","category_source","아이디어•건축•창업","apply_start","2026-11-02","apply_end","2027-01-10","source_url","https://www.contestkorea.com/view?str_no="+id));}

    @Test void supplementsRemainAfterApiRefreshAndEmptyCellsAndExclusionHidesAllPublicReads(){
        api.save(ContestKoreaCategory.IDEA,List.of(item("supplement","아이디어•건축•창업")));
        var row=row("supplement");row.put("topic","검증된 주제");row.put("team_min","2");row.put("exclude_reason","원문 확인 후 제외");
        row.put("consent_requirements","참가자 개인정보 동의서 제출");
        var result=csv.apply(List.of(row),"fixture");assertEquals(1,result.updated());assertEquals(1,result.dateConflicts());assertEquals(1,result.excluded());
        var empty=row("supplement");empty.put("topic","");empty.put("team_min","");empty.put("consent_requirements","");csv.apply(List.of(empty),"fixture");
        api.save(ContestKoreaCategory.IDEA,List.of(item("supplement","아이디어•건축•창업")));
        var saved=repository.findBySourceProviderAndExternalId("CONTEST_KOREA","supplement").orElseThrow();
        assertEquals("검증된 주제",saved.getTheme());assertEquals(2,saved.getMinMembers());assertNull(saved.getMaxMembers());
        assertEquals("참가자 개인정보 동의서 제출",saved.getConsentRequirements());
        assertEquals(LocalDate.of(2027,1,10),saved.getDeadline());assertEquals("CSV",saved.getDateSource());assertNotNull(saved.getApiDeadline());assertFalse(saved.isAvailable());
        assertTrue(repository.findByAnnouncementIdAndVisibilityTrue(saved.getAnnouncementId()).isEmpty());
    }
    @Test void multipleMembershipUsesPrimaryCategoryIndependentOfCollectionOrderAndConflictsAreHeld(){
        for(var id:List.of("order-a","order-b")){
            var categories=id.endsWith("a")?List.of(ContestKoreaCategory.IT,ContestKoreaCategory.IDEA):List.of(ContestKoreaCategory.IDEA,ContestKoreaCategory.IT);
            for(var category:categories)api.save(category,List.of(item(id,"아이디어•건축•창업")));
            var saved=repository.findBySourceProviderAndExternalId("CONTEST_KOREA",id).orElseThrow();
            assertEquals(AnnouncementField.IDEA_ARCHITECTURE_STARTUP,saved.getField());assertEquals("030310001|031410001",saved.getExternalCategoryCodes());
            api.save(ContestKoreaCategory.IT,List.of(item(id,"학문•과학•IT")));
            assertNull(saved.getField());assertFalse(saved.isAvailable());assertNotNull(saved.getClassificationIssue());
        }
    }
    @Test void unknownCategoriesAreReportedAndRequiredFieldsValidateBeforeCreation(){
        var unsupported=row("photo");unsupported.put("category_source","사진•영상");
        var invalid=row("invalid");invalid.put("apply_start","11.02");
        var result=csv.apply(List.of(unsupported,invalid,row("created")),"fixture");
        assertEquals(1,result.unmatched());assertEquals(1,result.failed());assertEquals(1,result.created());
        assertTrue(repository.findBySourceProviderAndExternalId("CONTEST_KOREA","photo").isEmpty());
        assertNull(repository.findBySourceProviderAndExternalId("CONTEST_KOREA","created").orElseThrow().getMinMembers());
    }
    @Test void unsupportedApiCategoryIsNotForcedIntoRequestedField(){
        var result=api.save(ContestKoreaCategory.IT,List.of(item("api-photo","사진•영상•영화제")));
        assertEquals(1,result.excluded());
        var saved=repository.findBySourceProviderAndExternalId("CONTEST_KOREA","api-photo").orElseThrow();
        assertNull(saved.getField());assertEquals("UNSUPPORTED_API_CATEGORY",saved.getClassificationIssue());
    }
    @Test void knownMaximumDoesNotMakeUnknownMinimumUnlimited(){
        var announcement=com.savanna.backend.domain.Announcement.builder().title("부분 인원").deadline(LocalDate.now().plusDays(1)).maxMembers(5).enrollmentRequirement("누구나").build();
        var response=new com.savanna.backend.service.AnnouncementEligibilityEvaluator().evaluate(announcement,1L,
                new com.savanna.backend.service.EligibilityTeamReader.TeamSnapshot(1L,List.of(com.savanna.backend.enums.EnrollmentStatus.ENROLLED)));
        assertEquals(com.savanna.backend.enums.EligibilityStatus.TEAM_INFO_REQUIRED,response.status());
    }
}

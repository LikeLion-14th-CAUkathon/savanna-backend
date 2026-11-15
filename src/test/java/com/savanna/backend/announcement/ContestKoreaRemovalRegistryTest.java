package com.savanna.backend.announcement;
import com.savanna.backend.integration.contestkorea.*;
import com.savanna.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="recommendation.ai.enabled=false") @Transactional
class ContestKoreaRemovalRegistryTest {
    @Autowired ContestKoreaWriter api;
    @Autowired ContestKoreaCsvWriter csv;
    @Autowired AnnouncementRepository announcements;
    @Autowired AnnouncementSourceEventRepository events;
    @Test void apiAndCsvCannotRecreateAnyExplicitlyDeletedContestOrItsSourceEvents(){
        long before=announcements.count(),beforeEvents=events.count();
        var items=ContestKoreaRemovalRegistry.REMOVED.entrySet().stream().map(e->new ContestKoreaItem(e.getKey(),"미술•디자인•웹툰",e.getValue(),"주최","누구나","11.01~12.31","https://www.contestkorea.com/view?str_no="+e.getKey(),null,"전국")).toList();
        var imported=api.save(ContestKoreaCategory.DESIGN,items);assertEquals(10,imported.suppressedIds().size());assertEquals(0,imported.created());assertTrue(imported.skippedIds().isEmpty());
        var rows=ContestKoreaRemovalRegistry.REMOVED.keySet().stream().map(id->Map.of("api_id",id,"category_source","미술•디자인•웹툰")).toList();
        var supplemented=csv.apply(rows,"fixture");assertEquals(10,supplemented.suppressed());assertEquals(0,supplemented.failed());assertEquals(before,announcements.count());assertEquals(beforeEvents,events.count());
    }
    @Test void correctedTargetTextOverridesRawAndSurvivesApiRefresh(){
        var item=new ContestKoreaItem("retained-target-text","아이디어•건축•창업","남길 공고","주최","해당자 ▶","11.01~12.31","https://www.contestkorea.com/view?str_no=retained-target-text",null,"온라인");
        api.save(ContestKoreaCategory.IDEA,List.of(item));
        csv.apply(List.of(Map.of("api_id",item.id(),"category_source","아이디어•건축•창업","target_raw","해당자 ▶","target_text","성남시 청년(만 19~39세)")),"fixture");
        var saved=announcements.findBySourceProviderAndExternalId("CONTEST_KOREA",item.id()).orElseThrow();assertEquals("성남시 청년(만 19~39세)",saved.getEnrollmentRequirement());assertTrue(saved.getCsvMetadata().contains("해당자"));
        api.save(ContestKoreaCategory.IDEA,List.of(item));assertEquals("성남시 청년(만 19~39세)",saved.getEnrollmentRequirement());
    }
}

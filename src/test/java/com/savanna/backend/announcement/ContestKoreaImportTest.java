package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.*;
import com.savanna.backend.repository.AnnouncementRepository;
import com.savanna.backend.service.AnnouncementEligibilityEvaluator;
import com.savanna.backend.service.EligibilityTeamReader.TeamSnapshot;
import com.savanna.backend.dto.response.AnnouncementDetailResponse;
import com.savanna.backend.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ContestKoreaImportTest {
    @Autowired ContestKoreaWriter writer;
    @Autowired AnnouncementRepository repository;
    @Autowired AnnouncementEligibilityEvaluator evaluator;

    ContestKoreaItem item(String id, String period, String target, String area) {
        return new ContestKoreaItem(id, "아이디어•건축•창업", " 공고 제목 ", " 주최 기관 ", target, period,
                "https://www.contestkorea.com/sub/view.php?str_no=" + id,
                "https://www.contestkorea.com/poster.jpg", area);
    }

    @Test void importsDatesSourceFieldsAndUpdatesSameIdWithoutOverwritingManualDetails() {
        var result = writer.save(ContestKoreaCategory.IDEA,
                List.of(item("202611030022", "10.30~11.18", "대학생", "온라인")));
        assertEquals(1, result.created());
        var saved = repository.findBySourceProviderAndExternalId("CONTEST_KOREA", "202611030022").orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 30).atStartOfDay(), saved.getApplicationStartsAt());
        assertEquals(LocalDate.of(2026, 11, 18), saved.getDeadline());
        assertEquals("공고 제목", saved.getTitle());
        assertEquals("주최 기관", saved.getHost());
        assertEquals(AnnouncementField.IDEA_ARCHITECTURE_STARTUP, saved.getField());
        assertNull(saved.getRegion());
        assertNull(saved.getApplyUrl());
        assertEquals("온라인", AnnouncementDetailResponse.from(saved).participationRegion());
        assertNotNull(saved.getLastSyncedAt());
        saved.changeVisibility(false);
        var update = writer.save(ContestKoreaCategory.IDEA,
                List.of(item("202611030022", "10.30~11.20", "해당자 ▶", "경 기")));
        repository.flush();
        assertEquals(0, update.created());
        assertEquals(1, update.updated());
        assertEquals(LocalDate.of(2026, 11, 20), saved.getDeadline());
        assertEquals("온라인", saved.getParticipationRegion());
        assertEquals("10.30~11.20", saved.getApplicationPeriodRaw());
        assertFalse(saved.isVisibility());
        assertEquals(1, repository.findAll().stream().filter(a -> "202611030022".equals(a.getExternalId())).count());
    }

    @Test void yearRolloverUses2027AndInvalidDatesAreSkippedWithoutDamagingExistingData() {
        writer.save(ContestKoreaCategory.IDEA, List.of(item("rollover", "11.18-01.23", "해당자 ▶", "온라인")));
        var saved = repository.findBySourceProviderAndExternalId("CONTEST_KOREA", "rollover").orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 18).atStartOfDay(), saved.getApplicationStartsAt());
        assertEquals(LocalDate.of(2027, 1, 23), saved.getDeadline());
        var result = writer.save(ContestKoreaCategory.IDEA, List.of(
                item("rollover", "추후 공지", "해당자 ▶", "온라인"),
                item("bad-leap", "02.29~03.01", "대학생", "온라인")));
        assertEquals(List.of("rollover", "bad-leap"), result.skippedIds());
        assertEquals(LocalDate.of(2027, 1, 23), saved.getDeadline());
    }

    @Test void additionalRequirementsAndRegionalEligibilityExplainNeedForOriginalNotice() {
        writer.save(ContestKoreaCategory.IDEA, List.of(item("conditions", "11.02~11.16",
                "누구나, 대학생, 해당자 ▶", "경 북")));
        var saved = repository.findBySourceProviderAndExternalId("CONTEST_KOREA", "conditions").orElseThrow();
        var response = evaluator.evaluate(saved, 1L, new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED)));
        assertEquals(EligibilityStatus.TEAM_INFO_REQUIRED, response.status());
        assertTrue(response.conditions().stream().anyMatch(c -> c.code().equals("ADDITIONAL_REQUIREMENTS") && c.message().contains("공고 상세 원문")));
        assertTrue(response.conditions().stream().anyMatch(c -> c.code().equals("PARTICIPATION_REGION")));
        var withoutTeam = evaluator.evaluate(saved, null, null);
        assertTrue(withoutTeam.conditions().stream().anyMatch(c -> c.code().equals("ADDITIONAL_REQUIREMENTS")));
    }
}

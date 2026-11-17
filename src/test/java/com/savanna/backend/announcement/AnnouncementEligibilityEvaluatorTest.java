package com.savanna.backend.announcement;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.service.AnnouncementEligibilityEvaluator;
import com.savanna.backend.service.EligibilityTeamReader.TeamSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Arrays;
import java.util.List;
import static com.savanna.backend.enums.EligibilityStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementEligibilityEvaluatorTest {
    final AnnouncementEligibilityEvaluator evaluator = new AnnouncementEligibilityEvaluator();

    @Test void attendanceRegionMatchesEveryMemberSettings() {
        var notice = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").region("서 울 특별시").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.GRADUATED),
                List.of(List.of("SEOUL", "BUSAN"), List.of("SEOUL")));
        assertEquals(SATISFIED, evaluator.evaluate(notice, 1L, team).status());
        var mismatch = new TeamSnapshot(1L, team.memberEnrollmentStatuses(),
                List.of(List.of("SEOUL"), List.of("BUSAN")));
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(notice, 1L, mismatch).status());
        var missing = new TeamSnapshot(1L, team.memberEnrollmentStatuses(), List.of(List.of("SEOUL"), List.of()));
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(notice, 1L, missing).status());
    }

    @Test void eligibilityRegionAloneDoesNotRequireAttendanceCheck() {
        var notice = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").participationRegion("서울").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED));
        var result = evaluator.evaluate(notice, 1L, team);
        assertEquals(SATISFIED, result.status());
        assertTrue(result.conditions().stream().noneMatch(c -> c.code().equals("PARTICIPATION_REGION")));
    }

    @Test void onlineBypassesMismatchingAttendanceSettings() {
        var notice = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").region("서울").venueNote("온라인").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED),
                List.of(List.of("BUSAN"), List.of()));
        assertEquals(SATISFIED, evaluator.evaluate(notice, 1L, team).status());
    }

    @Test void offlineVenueUsesProvinceAliasesAndUnknownVenueRequiresConfirmation() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED),
                List.of(List.of("CHUNGNAM"), List.of("CHUNGNAM")));
        var notice = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").venueNote("오프라인 · 충청남도 천안").build();
        assertEquals(SATISFIED, evaluator.evaluate(notice, 1L, team).status());
        var unknown = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").venueNote("오프라인 · 장소 추후 공지").build();
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(unknown, 1L, team).status());
    }

    Announcement announcement(String requirement) {
        return Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement(requirement).build();
    }

    @ParameterizedTest
    @CsvSource({"온라인,,", "서울,온라인,", "서울,,온라인 · 서울", "서울,,온　라　인", "서울,,온라인·오프라인 병행"})
    void onlineMarkerSatisfiesRegionRegardlessOfRegionalRestriction(String participationRegion, String region, String venueNote) {
        var announcement = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").participationRegion(participationRegion)
                .region(region).venueNote(venueNote).build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.GRADUATED));
        var result = evaluator.evaluate(announcement, 1L, team);
        assertEquals(SATISFIED, result.status());
        assertTrue(result.conditions().stream().anyMatch(c -> c.code().equals("PARTICIPATION_REGION") && c.status() == SATISFIED));
    }

    @Test void offlineRegionalRestrictionStillRequiresConfirmation() {
        var announcement = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("누구나").participationRegion("서울")
                .venueNote("오프라인 · 서울").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED));
        var result = evaluator.evaluate(announcement, 1L, team);
        assertTrue(result.conditions().stream().anyMatch(c -> c.code().equals("PARTICIPATION_REGION") && c.status() == TEAM_INFO_REQUIRED));
    }

    @Test void onlineDoesNotOverrideEnrollmentAndMemberCountIsNotChecked() {
        var announcement = Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement("재학생").participationRegion("온라인").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.GRADUATED));
        var result = evaluator.evaluate(announcement, 1L, team);
        assertEquals(MISMATCH, result.status());
        assertTrue(result.conditions().stream().noneMatch(c -> c.code().equals("MEMBER_COUNT")));
        assertTrue(result.conditions().stream().anyMatch(c -> c.code().equals("ENROLLMENT") && c.status() == MISMATCH));
    }

    @Test void allKnownConditionsSatisfied() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ON_LEAVE));
        assertEquals(SATISFIED, evaluator.evaluate(announcement("재학생 및 휴학생"), 1L, team).status());
    }

    @Test void tooFewMembersDoesNotCauseMismatch() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED));
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(announcement("별도 자격"), 1L, team).status());
    }

    @Test void tooManyMembersDoesNotCauseMismatch() {
        var team = new TeamSnapshot(1L, java.util.Collections.nCopies(5, EnrollmentStatus.ENROLLED));
        assertEquals(SATISFIED, evaluator.evaluate(announcement("재학생"), 1L, team).status());
    }

    @Test void individualOnlyAndUnknownLimitsDoNotExcludeTeams() {
        var team = new TeamSnapshot(1L, java.util.Collections.nCopies(5, EnrollmentStatus.ENROLLED));
        for (Integer max : Arrays.asList(1, null)) {
            var notice = Announcement.builder().maxMembers(max).enrollmentRequirement("누구나").build();
            var result = evaluator.evaluate(notice, 1L, team);
            assertEquals(SATISFIED, result.status());
            assertTrue(result.conditions().stream().noneMatch(c -> c.code().equals("MEMBER_COUNT")));
        }
    }

    @Test void enrollmentMismatch() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.GRADUATED));
        assertEquals(MISMATCH, evaluator.evaluate(announcement("재학생"), 1L, team).status());
    }

    @Test void missingTeamRequiresInfo() {
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(announcement("재학생"), null, null).status());
    }

    @Test void missingEnrollmentRequiresInfo() {
        var team = new TeamSnapshot(1L, Arrays.asList(EnrollmentStatus.ENROLLED, null));
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(announcement("재학생"), 1L, team).status());
    }

    @Test void complexTextMustNotBeGuessed() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED));
        assertEquals(TEAM_INFO_REQUIRED,
                evaluator.evaluate(announcement("재학생 중 서울 소재 대학의 디자인 전공자"), 1L, team).status());
    }

}

package com.savanna.backend.announcement;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.service.AnnouncementEligibilityEvaluator;
import com.savanna.backend.service.EligibilityTeamReader.TeamSnapshot;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static com.savanna.backend.enums.EligibilityStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementEligibilityEvaluatorTest {
    final AnnouncementEligibilityEvaluator evaluator = new AnnouncementEligibilityEvaluator();

    Announcement announcement(String requirement) {
        return Announcement.builder().minMembers(2).maxMembers(4)
                .enrollmentRequirement(requirement).build();
    }

    @Test void allKnownConditionsSatisfied() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ON_LEAVE));
        assertEquals(SATISFIED, evaluator.evaluate(announcement("재학생 및 휴학생"), 1L, team).status());
    }

    @Test void tooFewMembersMismatchEvenWhenEnrollmentUnknown() {
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED));
        assertEquals(MISMATCH, evaluator.evaluate(announcement("별도 자격"), 1L, team).status());
    }

    @Test void tooManyMembersMismatch() {
        var team = new TeamSnapshot(1L, java.util.Collections.nCopies(5, EnrollmentStatus.ENROLLED));
        assertEquals(MISMATCH, evaluator.evaluate(announcement("재학생"), 1L, team).status());
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

    @Test void additionalRestrictionsRequireReview() {
        var a = Announcement.builder().minMembers(2).enrollmentRequirement("재학생")
                .restrictions("수상 경력자 제외").build();
        var team = new TeamSnapshot(1L, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ENROLLED));
        assertEquals(TEAM_INFO_REQUIRED, evaluator.evaluate(a, 1L, team).status());
    }
}

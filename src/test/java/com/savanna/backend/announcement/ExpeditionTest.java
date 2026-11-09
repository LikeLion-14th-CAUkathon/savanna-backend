package com.savanna.backend.announcement;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.domain.Expedition;
import com.savanna.backend.enums.ExpeditionStage;
import com.savanna.backend.error.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionTest {
    private final LocalDate deadline = LocalDate.of(2026, 12, 20);

    private Expedition expedition() {
        return new Expedition(1L, Announcement.builder().title("공고")
                .deadline(deadline.atTime(18, 0)).build());
    }

    @Test void startsAtKickoffWithoutScheduleOrSubmission() {
        var e = expedition();
        assertEquals(ExpeditionStage.KICKOFF, e.getStage());
        assertNull(e.getKickoffDate());
        assertNull(e.getSubmitTargetDate());
        assertFalse(e.isSubmissionCompleted());
    }

    @Test void rejectsInvalidReferences() {
        assertThrows(BusinessException.class, () -> new Expedition(0L, expedition().getAnnouncement()));
        assertThrows(BusinessException.class, () -> new Expedition(null, expedition().getAnnouncement()));
        assertThrows(BusinessException.class, () -> new Expedition(1L, null));
    }

    @Test void allowsSameDayAndPartialSchedules() {
        var e = expedition();
        e.updateSchedule(deadline, deadline, deadline, deadline);
        assertEquals(deadline, e.getSubmitTargetDate());
        e.updateSchedule(deadline.minusDays(3), null, deadline, null);
        assertNull(e.getDraftDate());
        assertEquals(deadline, e.getReviewDate());
    }

    @Test void rejectsInvalidScheduleWithoutChangingPreviousValues() {
        var e = expedition();
        e.updateSchedule(deadline.minusDays(3), null, deadline, deadline);
        assertThrows(BusinessException.class, () -> e.updateSchedule(deadline, null, deadline.minusDays(1), null));
        assertEquals(deadline.minusDays(3), e.getKickoffDate());
        assertThrows(BusinessException.class, () -> e.updateSchedule(null, null, null, deadline.plusDays(1)));
    }

    @Test void submissionTracksStageAndRepeatedSubmissionKeepsTimestamp() {
        var e = expedition();
        e.changeStage(ExpeditionStage.SUBMITTED);
        assertTrue(e.isSubmissionCompleted());
        var submittedAt = e.getSubmissionCompletedAt();
        e.changeStage(ExpeditionStage.SUBMITTED);
        assertEquals(submittedAt, e.getSubmissionCompletedAt());
        e.changeStage(ExpeditionStage.REVIEW);
        assertFalse(e.isSubmissionCompleted());
        assertThrows(BusinessException.class, () -> e.changeStage(null));
        assertEquals(ExpeditionStage.REVIEW, e.getStage());
    }
}

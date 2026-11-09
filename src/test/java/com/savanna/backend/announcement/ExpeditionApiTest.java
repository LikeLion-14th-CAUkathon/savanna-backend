package com.savanna.backend.announcement;

import com.savanna.backend.controller.ExpeditionController;
import com.savanna.backend.domain.*;
import com.savanna.backend.dto.request.*;
import com.savanna.backend.enums.ExpeditionStage;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ExpeditionApiTest.TeamConfig.class)
@Transactional
class ExpeditionApiTest {
    @TestConfiguration
    static class TeamConfig {
        @Bean @Primary ExpeditionTeamAccess testTeamAccess() {
            return (principal, teamId, write) -> {
                if (!"member".equals(principal.getName()) || teamId != 1L)
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                return 7L;
            };
        }
    }

    @Autowired ExpeditionService service;
    @Autowired ExpeditionRepository repository;
    @Autowired AnnouncementRepository announcements;
    @Autowired ExpeditionStageHistoryRepository histories;
    MockMvc mvc;
    Expedition expedition;
    final LocalDate deadline = LocalDate.of(2026, 12, 30);

    @BeforeEach void setup() {
        var a = announcements.saveAndFlush(Announcement.builder().title("공고")
                .deadline(deadline.atTime(18, 0)).build());
        expedition = repository.saveAndFlush(new Expedition(1L, a));
        mvc = MockMvcBuilders.standaloneSetup(new ExpeditionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    String path() { return "/api/v1/expeditions/" + expedition.getExpeditionId(); }

    @Test void detailIncludesAnnouncementAndSchedule() throws Exception {
        mvc.perform(get(path()).principal(() -> "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement.title").value("공고"))
                .andExpect(jsonPath("$.stage").value("KICKOFF"))
                .andExpect(jsonPath("$.submissionCompleted").value(false));
    }

    @Test void submissionCreatesHistoryWithAuthenticatedUser() throws Exception {
        mvc.perform(patch(path() + "/stage").principal(() -> "member")
                        .contentType("application/json").content("{\"stage\":\"SUBMITTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionCompleted").value(true));
        var page = service.getStageHistories(expedition.getExpeditionId(), 0, 10, () -> "member");
        assertEquals(1, page.getTotalElements());
        assertEquals(7L, page.getContent().get(0).changedBy());
        assertEquals(ExpeditionStage.KICKOFF, page.getContent().get(0).fromStage());
        assertEquals(ExpeditionStage.SUBMITTED, page.getContent().get(0).toStage());
    }

    @Test void sameStageDoesNotDuplicateHistory() {
        service.changeStage(expedition.getExpeditionId(),
                new ExpeditionStageRequest(ExpeditionStage.DRAFT), () -> "member");
        service.changeStage(expedition.getExpeditionId(),
                new ExpeditionStageRequest(ExpeditionStage.DRAFT), () -> "member");
        assertEquals(1, service.getStageHistories(expedition.getExpeditionId(), 0, 10, () -> "member")
                .getTotalElements());
    }

    @Test void historiesAreNewestFirst() {
        service.changeStage(expedition.getExpeditionId(),
                new ExpeditionStageRequest(ExpeditionStage.DRAFT), () -> "member");
        service.changeStage(expedition.getExpeditionId(),
                new ExpeditionStageRequest(ExpeditionStage.REVIEW), () -> "member");
        var page = service.getStageHistories(expedition.getExpeditionId(), 0, 10, () -> "member");
        assertEquals(ExpeditionStage.REVIEW, page.getContent().get(0).toStage());
    }

    @Test void partialScheduleKeepsExistingDates() throws Exception {
        expedition.updateSchedule(deadline.minusDays(5), deadline.minusDays(4), null, null);
        mvc.perform(patch(path() + "/schedule").principal(() -> "member")
                        .contentType("application/json")
                        .content("{\"reviewDate\":\"2026-12-28\",\"submitTargetDate\":\"2026-12-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kickoffDate").value("2026-12-25"))
                .andExpect(jsonPath("$.reviewDate").value("2026-12-28"));
    }

    @Test void invalidScheduleReturns400() throws Exception {
        mvc.perform(patch(path() + "/schedule").principal(() -> "member")
                        .contentType("application/json").content("{\"submitTargetDate\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest());
        assertNull(expedition.getSubmitTargetDate());
    }

    @Test void missingAndInvalidRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/expeditions/" + Long.MAX_VALUE).principal(() -> "member"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expeditions/0").principal(() -> "member"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(path() + "/stage").principal(() -> "member")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path() + "/stage-histories").principal(() -> "member").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test void unauthenticatedAndOtherUsersCannotReadOrChange() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).principal(() -> "outsider")).andExpect(status().isForbidden());
        mvc.perform(patch(path() + "/stage").principal(() -> "outsider")
                        .contentType("application/json").content("{\"stage\":\"SUBMITTED\"}"))
                .andExpect(status().isForbidden());
        assertEquals(ExpeditionStage.KICKOFF, expedition.getStage());
        assertEquals(0, histories.count());
    }
}

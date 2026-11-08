package com.savanna.backend.announcement;

import com.savanna.backend.controller.ExpeditionTaskController;
import com.savanna.backend.domain.*;
import com.savanna.backend.error.GlobalExceptionHandler;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.ExpeditionTaskService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ExpeditionTestTeamConfig.class)
@Transactional
class ExpeditionTaskApiTest {
    @Autowired ExpeditionTaskService service;
    @Autowired ExpeditionTaskRepository tasks;
    @Autowired ExpeditionRepository expeditions;
    @Autowired AnnouncementRepository announcements;
    MockMvc mvc;
    Expedition expedition;
    final LocalDate date = LocalDate.of(2026, 12, 1);

    @BeforeEach void setup() {
        var a = announcements.saveAndFlush(Announcement.builder().title("공고")
                .deadline(date.plusDays(20).atTime(18, 0)).build());
        expedition = expeditions.saveAndFlush(new Expedition(1L, a));
        mvc = MockMvcBuilders.standaloneSetup(new ExpeditionTaskController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    String collection() { return "/api/v1/expeditions/" + expedition.getExpeditionId() + "/tasks"; }
    String item(ExpeditionTask task) { return "/api/v1/expedition-tasks/" + task.getTaskId(); }
    ExpeditionTask task(String title, LocalDate dueDate, LocalTime time) {
        return tasks.saveAndFlush(new ExpeditionTask(expedition, title, dueDate, time));
    }

    @Test void createsNoticeWithOptionalTime() throws Exception {
        mvc.perform(post(collection()).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"제출 양식 채우기\",\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("제출 양식 채우기"))
                .andExpect(jsonPath("$.dueTime").isEmpty())
                .andExpect(jsonPath("$.isDone").value(false))
                .andExpect(jsonPath("$.taskId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        assertEquals(1, tasks.count());
    }

    @Test void ordersByDateThenTimeWithUndatedTimeLastAndStableIdOrder() throws Exception {
        task("시간 미정", date, null);
        task("늦은 시간", date, LocalTime.of(20, 0));
        task("이른 시간", date, LocalTime.of(9, 0));
        task("이른 시간 2", date, LocalTime.of(9, 0));
        task("전날", date.minusDays(1), null);
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("전날"))
                .andExpect(jsonPath("$[1].title").value("이른 시간"))
                .andExpect(jsonPath("$[2].title").value("이른 시간 2"))
                .andExpect(jsonPath("$[3].title").value("늦은 시간"))
                .andExpect(jsonPath("$[4].title").value("시간 미정"));
    }

    @Test void listIsEmptyAndDoesNotIncludeOtherExpeditions() throws Exception {
        var other = expeditions.saveAndFlush(new Expedition(2L, expedition.getAnnouncement()));
        tasks.saveAndFlush(new ExpeditionTask(other, "다른 팀", date, null));
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void patchPreservesOmittedFieldsAndAllowsFalse() throws Exception {
        var task = task("기존 내용", date, LocalTime.of(9, 0));
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"isDone\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDone").value(true))
                .andExpect(jsonPath("$.title").value("기존 내용"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.dueTime").value("09:00:00"));
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"수정 내용\",\"isDone\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDone").value(false))
                .andExpect(jsonPath("$.dueTime").value("09:00:00"));
        assertFalse(tasks.findById(task.getTaskId()).orElseThrow().isDone());
    }

    @Test void patchCanChangeDateAndClearOrSetTime() throws Exception {
        var task = task("공지", date, LocalTime.of(9, 0));
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"dueDate\":\"2026-12-02\",\"dueTime\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-12-02"))
                .andExpect(jsonPath("$.dueTime").isEmpty());
        assertNull(task.getDueTime());
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"dueTime\":\"21:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueTime").value("21:00:00"));
    }

    @Test void deleteRemovesOnlyTaskAndReturns204() throws Exception {
        var task = task("삭제", date, null);
        task("유지", date, null);
        mvc.perform(delete(item(task)).principal(() -> "member"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        tasks.flush();
        assertFalse(tasks.existsById(task.getTaskId()));
        assertEquals(1, tasks.count());
        assertTrue(expeditions.existsById(expedition.getExpeditionId()));
    }

    @Test void invalidContentDatesTimesAndMissingDateReturn400() throws Exception {
        for (String body : new String[]{
                "{\"title\":\" \",\"dueDate\":\"2026-12-01\"}",
                "{\"title\":\"공지\"}",
                "{\"title\":\"공지\",\"dueDate\":\"2026-02-30\"}",
                "{\"title\":\"공지\",\"dueDate\":\"2026-12-01\",\"dueTime\":\"25:00\"}"}) {
            mvc.perform(post(collection()).principal(() -> "member")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        var task = task("원래 내용", date, null);
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertEquals("원래 내용", task.getTitle());
    }

    @Test void missingResourcesAndInvalidIdsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/expeditions/" + Long.MAX_VALUE + "/tasks").principal(() -> "member"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/expedition-tasks/" + Long.MAX_VALUE).principal(() -> "member"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expeditions/0/tasks").principal(() -> "member"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/expedition-tasks/abc").principal(() -> "member"))
                .andExpect(status().isBadRequest());
    }

    @Test void authenticationAndTeamPermissionsProtectEveryOperation() throws Exception {
        var task = task("비공개", date, null);
        mvc.perform(get(collection())).andExpect(status().isUnauthorized());
        mvc.perform(get(collection()).principal(() -> "outsider")).andExpect(status().isForbidden());
        mvc.perform(post(collection()).principal(() -> "outsider").contentType("application/json")
                        .content("{\"title\":\"공지\",\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(item(task)).principal(() -> "outsider").contentType("application/json")
                        .content("{\"isDone\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(item(task)).principal(() -> "outsider")).andExpect(status().isForbidden());
        assertEquals(1, tasks.count());
        assertFalse(task.isDone());
    }

    @Test void cannotAccessAnotherTeamsTaskEvenWhenKnowingId() throws Exception {
        var other = expeditions.saveAndFlush(new Expedition(2L, expedition.getAnnouncement()));
        var task = tasks.saveAndFlush(new ExpeditionTask(other, "다른 팀", date, null));
        mvc.perform(patch(item(task)).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"변경 시도\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(item(task)).principal(() -> "member")).andExpect(status().isForbidden());
        assertEquals("다른 팀", task.getTitle());
    }
}

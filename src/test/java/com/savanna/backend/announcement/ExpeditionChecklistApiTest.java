package com.savanna.backend.announcement;

import com.savanna.backend.controller.ExpeditionChecklistController;
import com.savanna.backend.domain.*;
import com.savanna.backend.error.GlobalExceptionHandler;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.ExpeditionChecklistService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(ExpeditionTestTeamConfig.class)
@Transactional
class ExpeditionChecklistApiTest {
    @Autowired ExpeditionChecklistService service;
    @Autowired ExpeditionChecklistRepository checklists;
    @Autowired ExpeditionRepository expeditions;
    @Autowired AnnouncementRepository announcements;
    MockMvc mvc;
    Expedition expedition;

    @BeforeEach void setup() {
        var a = announcements.saveAndFlush(Announcement.builder().title("공고")
                .deadline(LocalDateTime.now().plusDays(20).toLocalDate()).build());
        expedition = expeditions.saveAndFlush(new Expedition(1L, a));
        mvc = MockMvcBuilders.standaloneSetup(new ExpeditionChecklistController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    String collection() { return "/api/v1/expeditions/" + expedition.getExpeditionId() + "/checklists"; }
    String item(ExpeditionChecklist c) { return "/api/v1/expedition-checklists/" + c.getChecklistId(); }
    ExpeditionChecklist save(String title, int order) {
        return checklists.saveAndFlush(new ExpeditionChecklist(expedition, title, order));
    }

    @Test void emptyListReturnsZeroProgress() throws Exception {
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.progress").value("0/0"));
    }

    @Test void sortsByOrderThenIdAndCountsCheckedItems() throws Exception {
        var last = save("세 번째", 10);
        var first = save("첫 번째", 0);
        save("두 번째", 0);
        last.update(last.getTitle(), true, last.getSortOrder());
        first.update(first.getTitle(), true, first.getSortOrder());
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("첫 번째"))
                .andExpect(jsonPath("$.items[1].title").value("두 번째"))
                .andExpect(jsonPath("$.items[2].title").value("세 번째"))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.progress").value("2/3"));
    }

    @Test void appendsByDefaultAndAcceptsExplicitOrder() throws Exception {
        mvc.perform(post(collection()).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"기획서 PDF\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.isChecked").value(false));
        save("높은 순서", 8);
        mvc.perform(post(collection()).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"추가 항목\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(9));
        mvc.perform(post(collection()).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"앞 항목\",\"sortOrder\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test void checkAndUncheckAreExplicitAndIdempotent() throws Exception {
        var c = save("제출", 0);
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json")
                            .content("{\"isChecked\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.isChecked").value(true))
                    .andExpect(jsonPath("$.title").value("제출"));
        }
        mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json")
                        .content("{\"isChecked\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isChecked").value(false));
    }

    @Test void editsTitleAndOrderWithoutLosingCheckedState() throws Exception {
        var c = save("제출", 3);
        c.update("제출", true, 3);
        mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\"최종 기획서\",\"sortOrder\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("최종 기획서"))
                .andExpect(jsonPath("$.sortOrder").value(0)).andExpect(jsonPath("$.isChecked").value(true));
        mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isChecked").value(true));
    }

    @Test void deletionUpdatesProgressAndKeepsExpedition() throws Exception {
        var c = save("완료", 0);
        c.update(c.getTitle(), true, 0);
        save("미완료", 1);
        mvc.perform(delete(item(c)).principal(() -> "member")).andExpect(status().isNoContent());
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progress").value("0/1"));
        assertTrue(expeditions.existsById(expedition.getExpeditionId()));
    }

    @Test void invalidRequestsAre400AndDoNotMutateItem() throws Exception {
        for (String body : new String[]{"{}", "{\"title\":\" \"}", "{\"title\":\"제출\",\"sortOrder\":-1}"}) {
            mvc.perform(post(collection()).principal(() -> "member").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        var c = save("원본", 0);
        mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json")
                        .content("{\"title\":\" \",\"isChecked\":true}")).andExpect(status().isBadRequest());
        assertFalse(c.isChecked());
        assertEquals("원본", c.getTitle());
        mvc.perform(patch(item(c)).principal(() -> "member").contentType("application/json")
                        .content("{\"sortOrder\":-1}")).andExpect(status().isBadRequest());
    }

    @Test void missingResourcesAndBadIdsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/expeditions/" + Long.MAX_VALUE + "/checklists").principal(() -> "member"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/expedition-checklists/" + Long.MAX_VALUE).principal(() -> "member"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expeditions/0/checklists").principal(() -> "member"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/expedition-checklists/abc").principal(() -> "member"))
                .andExpect(status().isBadRequest());
    }

    @Test void permissionsProtectAllOperationsAndOtherTeamsItems() throws Exception {
        var c = save("원본", 0);
        mvc.perform(get(collection())).andExpect(status().isUnauthorized());
        mvc.perform(get(collection()).principal(() -> "outsider")).andExpect(status().isForbidden());
        mvc.perform(post(collection()).principal(() -> "outsider").contentType("application/json")
                        .content("{\"title\":\"추가\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(item(c)).principal(() -> "outsider").contentType("application/json")
                        .content("{\"isChecked\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete(item(c)).principal(() -> "outsider")).andExpect(status().isForbidden());
        var other = expeditions.saveAndFlush(new Expedition(2L, expedition.getAnnouncement()));
        var otherItem = checklists.saveAndFlush(new ExpeditionChecklist(other, "타 팀", 0));
        mvc.perform(patch(item(otherItem)).principal(() -> "member").contentType("application/json")
                        .content("{\"isChecked\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete(item(otherItem)).principal(() -> "member")).andExpect(status().isForbidden());
        mvc.perform(get(collection()).principal(() -> "member"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        assertFalse(c.isChecked());
        assertFalse(otherItem.isChecked());
    }
}

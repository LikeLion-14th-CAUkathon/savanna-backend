package com.savanna.backend.announcement;

import com.savanna.backend.controller.AnnouncementController;
import com.savanna.backend.domain.Announcement;
import com.savanna.backend.error.GlobalExceptionHandler;
import com.savanna.backend.repository.AnnouncementRepository;
import com.savanna.backend.service.AnnouncementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AnnouncementDetailTest {
    @Autowired AnnouncementRepository repository;
    @Autowired AnnouncementService service;
    @Autowired com.savanna.backend.service.AnnouncementEligibilityService eligibilityService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Controller + actual service/repository; security filters are outside this test's scope.
        mvc = MockMvcBuilders.standaloneSetup(new AnnouncementController(service, eligibilityService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Announcement save(LocalDateTime start, LocalDateTime deadline) {
        return repository.saveAndFlush(Announcement.builder()
                .title("공고 상세 테스트").host("주최기관")
                .applicationStartsAt(start).deadline(deadline)
                .enrollmentRequirement("대학생").minMembers(2).maxMembers(5)
                .region("서울").theme("환경").evaluationCriteria("독창성")
                .submissionRequirements("기획서 PDF").restrictions("중복 출품 불가")
                .benefits("멘토링").contactEmail("help@example.com")
                .sourceUrl("https://example.com/notice").applyUrl("https://example.com/apply")
                .cost(BigDecimal.ZERO).build());
    }

    @Test
    void eligibilityWithoutTeamIntegrationRequiresInfoAndDoesNotSave() throws Exception {
        Announcement announcement = save(null, LocalDateTime.now().plusDays(10));
        long before = repository.count();
        mvc.perform(get("/api/v1/announcements/{id}/eligibility", announcement.getAnnouncementId())
                        .param("teamId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEAM_INFO_REQUIRED"))
                .andExpect(jsonPath("$.teamId").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(before, repository.count());
    }

    @Test
    void eligibilityRejectsPrivateAndInvalidRequests() throws Exception {
        Announcement announcement = save(null, LocalDateTime.now().plusDays(10));
        announcement.changeVisibility(false);
        repository.flush();
        mvc.perform(get("/api/v1/announcements/{id}/eligibility", announcement.getAnnouncementId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/{id}/eligibility", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/0/eligibility"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/announcements/1/eligibility").param("teamId", "-1"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void returnsPublicAnnouncementDetails() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Announcement announcement = save(now.minusDays(1), now.plusDays(10));
        mvc.perform(get("/api/v1/announcements/{id}", announcement.getAnnouncementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcementId").value(announcement.getAnnouncementId()))
                .andExpect(jsonPath("$.title").value("공고 상세 테스트"))
                .andExpect(jsonPath("$.enrollmentRequirement").value("대학생"))
                .andExpect(jsonPath("$.minMembers").value(2))
                .andExpect(jsonPath("$.maxMembers").value(5))
                .andExpect(jsonPath("$.region").value("서울"))
                .andExpect(jsonPath("$.theme").value("환경"))
                .andExpect(jsonPath("$.evaluationCriteria").value("독창성"))
                .andExpect(jsonPath("$.submissionRequirements").value("기획서 PDF"))
                .andExpect(jsonPath("$.restrictions").value("중복 출품 불가"))
                .andExpect(jsonPath("$.benefits").value("멘토링"))
                .andExpect(jsonPath("$.contactEmail").value("help@example.com"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/notice"))
                .andExpect(jsonPath("$.applyUrl").value("https://example.com/apply"))
                .andExpect(jsonPath("$.cost").value(0))
                .andExpect(jsonPath("$.receptionStatus").value("OPEN"));
    }

    @Test
    void returnsClosedPublicAnnouncement() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Announcement announcement = save(now.minusDays(10), now.minusDays(1));
        mvc.perform(get("/api/v1/announcements/{id}", announcement.getAnnouncementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptionStatus").value("CLOSED"));
    }

    @Test
    void returnsUpcomingAnnouncement() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Announcement announcement = save(now.plusDays(1), now.plusDays(10));
        mvc.perform(get("/api/v1/announcements/{id}", announcement.getAnnouncementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptionStatus").value("UPCOMING"));
    }

    @Test
    void hidesPrivateAnnouncement() throws Exception {
        Announcement announcement = save(null, LocalDateTime.now().plusDays(10));
        announcement.changeVisibility(false);
        repository.flush();
        mvc.perform(get("/api/v1/announcements/{id}", announcement.getAnnouncementId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingAnnouncementReturns404() throws Exception {
        mvc.perform(get("/api/v1/announcements/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidIdsReturn400() throws Exception {
        for (String id : new String[]{"0", "-1", "abc"}) {
            mvc.perform(get("/api/v1/announcements/" + id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }
}

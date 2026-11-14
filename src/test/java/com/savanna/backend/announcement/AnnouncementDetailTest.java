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

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AnnouncementDetailTest {
    @Autowired AnnouncementRepository repository;
    @Autowired AnnouncementService service;
    @Autowired com.savanna.backend.service.AnnouncementEligibilityService eligibilityService;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;

    @Test
    void deadlineDayRemainsOpenAndListsUseTheSameDateBoundary() throws Exception {
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        var today = now.toLocalDate();
        var current = save(now.minusDays(2), today.atStartOfDay());
        var closed = save(now.minusDays(3), today.minusDays(1).atStartOfDay());
        mvc.perform(get("/api/v1/announcements/{id}", current.getAnnouncementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadline").value(today.toString()))
                .andExpect(jsonPath("$.receptionStatus").value("OPEN"));
        // 23:59:59에도 마감일 당일 공고는 OPEN이고 어제 공고만 CLOSED입니다.
        var atEndOfDay = today.atTime(23, 59, 59);
        for (var filter : new com.savanna.backend.enums.ReceptionStatus[]{null, com.savanna.backend.enums.ReceptionStatus.OPEN}) {
            var results = repository.findAll(com.savanna.backend.repository.AnnouncementSpecification.search(null, null, filter, atEndOfDay));
            org.junit.jupiter.api.Assertions.assertTrue(results.stream().anyMatch(a -> a.getAnnouncementId().equals(current.getAnnouncementId())));
            org.junit.jupiter.api.Assertions.assertFalse(results.stream().anyMatch(a -> a.getAnnouncementId().equals(closed.getAnnouncementId())));
        }
        var results = repository.findAll(com.savanna.backend.repository.AnnouncementSpecification.search(
                null, null, com.savanna.backend.enums.ReceptionStatus.CLOSED, today.atStartOfDay()));
        org.junit.jupiter.api.Assertions.assertTrue(results.stream().anyMatch(a -> a.getAnnouncementId().equals(closed.getAnnouncementId())));
        org.junit.jupiter.api.Assertions.assertFalse(results.stream().anyMatch(a -> a.getAnnouncementId().equals(current.getAnnouncementId())));
    }

    @Test
    void createAcceptsDateOnlyAndSameDayStartButRejectsTimestampAndReversedDates() throws Exception {
        String request = """
                {"title":"날짜 공고", "host":"주최", "field":"IDEA_ARCHITECTURE_STARTUP",
                 "applicationStartsAt":"2026-12-20T18:00:00", "deadline":"%s"}
                """;
        mvc.perform(post("/api/v1/announcements").contentType("application/json").content(request.formatted("2026-12-20")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.deadline").value("2026-12-20"));
        for (String invalid : new String[]{"2026-12-19", "2026-12-20T18:00:00"}) {
            mvc.perform(post("/api/v1/announcements").contentType("application/json").content(request.formatted(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createsPersistsAndReturnsFormConsentAndApplicationDetails() throws Exception {
        var created=mvc.perform(post("/api/v1/announcements").contentType("application/json").content("""
                {"title":"상세 보완 테스트", "host":"테스트 주최", "field":"IDEA_ARCHITECTURE_STARTUP", "deadline":"2026-12-31",
                 "enrollmentRequirement":"대학생", "minMembers":2, "maxMembers":5,
                 "applicationInstructions":"  주최 측 접수 페이지에서 온라인 제출  ",
                 "submissionRequirements":"아이디어 기획서 PDF, 참가 신청서",
                 "formNote":"주최 측 지정 양식 사용", "lengthNote":"10쪽 이내",
                 "consentRequirements":"  개인정보 수집·이용 동의서 · 팀원 전원 서명  ",
                 "participationRegion":"전국", "region":"서울", "venueNote":"온라인 · 서울 현장 발표"}
                """))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id=tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getContentAsString()).get("announcementId").asLong();
        repository.flush();entityManager.clear();
        mvc.perform(get("/api/v1/announcements/{id}",id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationInstructions").value("주최 측 접수 페이지에서 온라인 제출"))
                .andExpect(jsonPath("$.formNote").value("주최 측 지정 양식 사용"))
                .andExpect(jsonPath("$.lengthNote").value("10쪽 이내"))
                .andExpect(jsonPath("$.consentRequirements").value("개인정보 수집·이용 동의서 · 팀원 전원 서명"))
                .andExpect(jsonPath("$.participationRegion").value("전국"))
                .andExpect(jsonPath("$.venueNote").value("온라인 · 서울 현장 발표"));
    }

    @Test
    void absentConsentIsExplicitJsonNullAndDoesNotMeanConsentIsUnnecessary() throws Exception {
        var announcement=save(null,LocalDateTime.now().plusDays(5));
        var response=mvc.perform(get("/api/v1/announcements/{id}",announcement.getAnnouncementId()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var tree=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(tree.has("consentRequirements"));
        org.junit.jupiter.api.Assertions.assertTrue(tree.get("consentRequirements").isNull());
        for(String unknown:new String[]{"", "  ", "원문 참고"}){
            var value=Announcement.builder().title("테스트").deadline(LocalDateTime.now().toLocalDate()).consentRequirements(unknown).build();
            org.junit.jupiter.api.Assertions.assertNull(value.getConsentRequirements());
        }
    }

    @Test
    void rejectsOversizedConsent() throws Exception {
        var request=new java.util.LinkedHashMap<String,Object>();
        request.put("title","테스트");request.put("host","테스트");request.put("field","IDEA_ARCHITECTURE_STARTUP");request.put("deadline","2026-12-31");
        request.put("consentRequirements","가".repeat(10001));
        mvc.perform(post("/api/v1/announcements").contentType("application/json")
                        .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @BeforeEach
    void setUp() {
        // Controller + actual service/repository; security filters are outside this test's scope.
        mvc = MockMvcBuilders.standaloneSetup(new AnnouncementController(service, eligibilityService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Announcement save(LocalDateTime start, LocalDateTime deadline) {
        return repository.saveAndFlush(Announcement.builder()
                .title("공고 상세 테스트").host("주최기관")
                .applicationStartsAt(start).deadline(deadline.toLocalDate())
                .enrollmentRequirement("대학생").minMembers(2).maxMembers(5)
                .region("서울").theme("환경").evaluationCriteria("독창성")
                .submissionRequirements("기획서 PDF")
                .sourceUrl("https://example.com/notice").applyUrl("https://example.com/apply")
                .build());
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
                .andExpect(jsonPath("$.restrictions").doesNotExist())
                .andExpect(jsonPath("$.benefits").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/notice"))
                .andExpect(jsonPath("$.applyUrl").value("https://example.com/apply"))
                .andExpect(jsonPath("$.cost").doesNotExist())
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

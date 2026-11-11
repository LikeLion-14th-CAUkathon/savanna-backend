package com.savanna.backend.announcement;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.domain.Expedition;
import com.savanna.backend.enums.ExpeditionStage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ExpeditionPersistenceTest {
    @Autowired EntityManager entityManager;

    private Announcement announcement() {
        var a = Announcement.builder().title("공고").deadline(LocalDateTime.now().plusDays(10).toLocalDate()).build();
        entityManager.persist(a);
        return a;
    }

    @Test void persistsAndLoadsReferencesStageAndTimestamps() {
        var a = announcement();
        var e = new Expedition(1L, a);
        entityManager.persist(e);
        entityManager.flush();
        Long id = e.getExpeditionId();
        entityManager.clear();
        var loaded = entityManager.find(Expedition.class, id);
        assertEquals(1L, loaded.getTeamId());
        assertEquals(a.getAnnouncementId(), loaded.getAnnouncement().getAnnouncementId());
        assertEquals(ExpeditionStage.KICKOFF, loaded.getStage());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        loaded.changeStage(ExpeditionStage.DRAFT);
        entityManager.flush();
        assertFalse(loaded.getUpdatedAt().isBefore(loaded.getCreatedAt()));
    }

    @Test void databaseRejectsDuplicateTeamAndAnnouncement() {
        var a = announcement();
        entityManager.persist(new Expedition(1L, a));
        entityManager.flush();
        assertThrows(RuntimeException.class, () -> {
            entityManager.persist(new Expedition(1L, a));
            entityManager.flush();
        });
    }

    @Test void differentTeamsCanPrepareSameAnnouncement() {
        var a = announcement();
        entityManager.persist(new Expedition(1L, a));
        entityManager.persist(new Expedition(2L, a));
        assertDoesNotThrow(() -> entityManager.flush());
    }
}

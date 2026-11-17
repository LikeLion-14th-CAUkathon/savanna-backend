package com.savanna.backend.announcement;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.domain.Expedition;
import com.savanna.backend.dto.response.ExpeditionResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionMemberCountNoticeTest {
    @Test void warnsForKnownMinimumAndMaximumWithCurrentJoinedCount() {
        var solo = new Expedition(1L, Announcement.builder().minMembers(1).maxMembers(1).build());
        assertEquals("현재 0명이 합류했어요. 최소 1명이 필요해요.", ExpeditionResponse.from(solo, 0).memberCountNotice());
        assertNull(ExpeditionResponse.from(solo, 1).memberCountNotice());
        assertEquals("현재 2명이 합류했어요. 최대 1명까지 참가할 수 있어요.", ExpeditionResponse.from(solo, 2).memberCountNotice());
        var unknown = new Expedition(1L, Announcement.builder().minMembers(3).build());
        assertEquals("현재 1명이 합류했어요. 최소 3명이 필요해요.", ExpeditionResponse.from(unknown, 1).memberCountNotice());
        assertNull(ExpeditionResponse.from(unknown, 20).memberCountNotice());
        var invalid = new Expedition(1L, Announcement.builder().maxMembers(0).build());
        assertNull(ExpeditionResponse.from(invalid, 2).memberCountNotice());
        var limits = new Expedition(1L, Announcement.builder().minMembers(3).maxMembers(5).build());
        assertEquals("현재 2명이 합류했어요. 최소 3명이 필요해요.", ExpeditionResponse.from(limits, 2).memberCountNotice());
        assertEquals("현재 6명이 합류했어요. 최대 5명까지 참가할 수 있어요.", ExpeditionResponse.from(limits, 6).memberCountNotice());
        for (long count : new long[]{3, 4, 5}) assertNull(ExpeditionResponse.from(limits, count).memberCountNotice());
        var noLimits = new Expedition(1L, Announcement.builder().build());
        assertNull(ExpeditionResponse.from(noLimits, 2).memberCountNotice());
    }
}

package com.savanna.backend.announcement;

import com.savanna.backend.service.AnnouncementAudiencePolicy;
import org.junit.jupiter.api.Test;
import static com.savanna.backend.service.AnnouncementAudiencePolicy.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementAudiencePolicyTest {
    private final AnnouncementAudiencePolicy policy=new AnnouncementAudiencePolicy();
    @Test void explicitYouthOnlyTargetsAreExcluded() {
        for(String target:new String[]{"초등학생, 중학생, 고등학생","청소년","유치원, 초등학생","중·고등학생","초중고생"})
            assertEquals(EXCLUDE,policy.assess("공모전",target).action(),target);
    }
    @Test void mixedAdultAndOpenTargetsAndYoungAdultsAreRetained() {
        for(String target:new String[]{"누구나, 초등학생, 중학생, 고등학생, 대학생, 대학원생, 일반인","고등학생 및 대학생","청소년, 성인","대학생","청년","누구나"})
            assertNotEquals(EXCLUDE,policy.assess("공모전",target).action(),target);
    }
    @Test void titleIsNotProofAndAmbiguousOrNegatedTargetsRequireReview() {
        assertEquals(REVIEW,policy.assess("청소년이 바꾸는 서울",null).action());
        assertEquals(REVIEW,policy.assess("청소년 IT학술대회 및 대학생 IT논문경진대회","고등학생, 대학생, 해당자 ▶").action());
        assertEquals(REVIEW,policy.assess("공모전","초등학생, 중학생, 대학생 제외").action());
        assertEquals(REVIEW,policy.assess("공모전","청소년, 해당자 ▶").action());
        assertEquals(KEEP,policy.assess("청년 아이디어 공모전",null).action());
    }
}

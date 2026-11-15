package com.savanna.backend.announcement;
import com.savanna.backend.domain.User;
import com.savanna.backend.integration.contestkorea.*;
import com.savanna.backend.repository.*;
import com.savanna.backend.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="recommendation.ai.enabled=false") @Transactional
class RequestedContestExclusionApiTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Autowired ContestKoreaWriter writer;
    @Autowired AnnouncementRepository announcements;
    @Test void theTwoPreviouslyHiddenContestsAreNowSuppressedBeforeImport() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user=users.saveAndFlush(new User("exclude-reader@example.invalid","exclude-reader"));
        String bearer="Bearer "+tokens.createAccessToken(user.getUserId());
        String[][] contests={{"202611040033","어린이 미래 상상 그림 공모전","초등학생"},{"202610170003","2026 ACEL 전국 청소년 미술 공모전","초등학생, 중학생, 고등학생"}};
        for(var contest:contests){
            var item=new ContestKoreaItem(contest[0],"미술•디자인•웹툰",contest[1],"주최",contest[2],"11.01~12.31","https://www.contestkorea.com/view?str_no="+contest[0],null,"전국");
            var result=writer.save(ContestKoreaCategory.DESIGN,List.of(item));
            assertEquals(List.of(contest[0]),result.suppressedIds());
            assertTrue(announcements.findBySourceProviderAndExternalId("CONTEST_KOREA",contest[0]).isEmpty());
            mvc.perform(get("/api/v1/announcements").param("keyword",contest[1]).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            writer.save(ContestKoreaCategory.DESIGN,List.of(item));
            assertTrue(announcements.findBySourceProviderAndExternalId("CONTEST_KOREA",contest[0]).isEmpty());
        }
    }
}

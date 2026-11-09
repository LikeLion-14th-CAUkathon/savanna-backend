package com.savanna.backend.announcement;

import com.savanna.backend.domain.User;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class SwaggerJwtSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void swaggerAndSchemaArePublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/announcements'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security").doesNotExist());
    }

    @Test void apiIsNotAnonymousEvenWithLocalProfile() throws Exception {
        mvc.perform(get("/api/v1/announcements")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/announcements").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void actualAccessTokenAuthenticatesApi() throws Exception {
        User user = users.saveAndFlush(new User("swagger-test@example.com", "swagger-test-sub"));
        mvc.perform(get("/api/v1/announcements")
                        .header("Authorization", "Bearer " + tokens.createAccessToken(user.getUserId())))
                .andExpect(status().isOk());
    }

    @Test void invalidAndRefreshTokensCannotAuthenticateApi() throws Exception {
        User user = users.saveAndFlush(new User("swagger-test@example.com", "swagger-test-sub"));
        mvc.perform(get("/api/v1/announcements").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/announcements")
                        .header("Authorization", "Bearer " + tokens.createRefreshToken(user.getUserId())))
                .andExpect(status().isUnauthorized());
    }

    @Test void googleLoginStillRedirectsToProviderAndCsrfRemainsRequired() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout")).andExpect(status().isForbidden());
    }
}

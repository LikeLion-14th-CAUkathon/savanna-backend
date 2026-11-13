package com.savanna.backend.auth;

import com.savanna.backend.config.AuthCookieFactory;
import com.savanna.backend.domain.User;
import com.savanna.backend.handler.OAuth2LoginSuccessHandler;
import com.savanna.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests production cookie/CORS policy and server validation, not browser third-party-cookie exemptions. */
@SpringBootTest(properties = {
        "recommendation.ai.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173,https://savanna-frontend.vercel.app",
        "app.oauth2.success-redirect-url=http://localhost:5173/login?google=success"
})
@ActiveProfiles("prod")
@Transactional
class CrossSiteLoginCookieFlowTest {
    @Autowired WebApplicationContext context;
    @Autowired OAuth2LoginSuccessHandler handler;
    @Autowired UserRepository users;
    MockMvc mvc;
    private static final String ORIGIN="http://localhost:5173";
    private final JsonMapper json=JsonMapper.builder().build();

    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

    private void assertRefreshCookie(Cookie cookie) {
        assertNotNull(cookie);assertTrue(cookie.getSecure());assertTrue(cookie.isHttpOnly());
        assertEquals("None",cookie.getAttribute("SameSite"));assertEquals("/api/v1/auth",cookie.getPath());
        assertNull(cookie.getDomain());
    }

    @Test void oauthCookieCsrfRotationAndLogoutAllUseSecureNoneEvenBehindHttpProxy() throws Exception {
        String sub="cross-site-test-"+UUID.randomUUID();
        users.saveAndFlush(new User(sub+"@example.invalid",sub));
        var principal=new DefaultOidcUser(List.of(),new OidcIdToken("test-id-token",Instant.now(),Instant.now().plusSeconds(60),Map.of("sub",sub)));
        var loginResponse=new MockHttpServletResponse();
        // Internal request is HTTP, as when an HTTPS reverse proxy terminates TLS.
        handler.onAuthenticationSuccess(new MockHttpServletRequest(),loginResponse,
                new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        assertEquals("http://localhost:5173/login?google=success",loginResponse.getRedirectedUrl());
        var refresh=loginResponse.getCookie("refreshToken");assertRefreshCookie(refresh);

        var csrfResponse=mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN,ORIGIN))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"))
                .andReturn().getResponse();
        var csrf=csrfResponse.getCookie("XSRF-TOKEN");assertNotNull(csrf);
        assertTrue(csrf.getSecure());assertFalse(csrf.isHttpOnly());assertEquals("None",csrf.getAttribute("SameSite"));
        assertEquals("/",csrf.getPath());assertNull(csrf.getDomain());
        String token=json.readTree(csrfResponse.getContentAsString()).get("csrfToken").asText();
        assertEquals(csrf.getValue(),token);

        var rotated=mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN,ORIGIN).cookie(refresh,csrf).header("X-XSRF-TOKEN",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn().getResponse().getCookie("refreshToken");
        assertRefreshCookie(rotated);assertNotEquals(refresh.getValue(),rotated.getValue());
        var cleared=mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN,ORIGIN).cookie(rotated,csrf).header("X-XSRF-TOKEN",token))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refreshToken");
        assertRefreshCookie(cleared);assertEquals(0,cleared.getMaxAge());
    }

    @Test void missingCsrfIs403ButAcceptedCsrfWithoutRefreshIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN))
                .andExpect(status().isForbidden());
        var response=mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN,ORIGIN))
                .andExpect(status().isOk()).andReturn().getResponse();
        var csrf=response.getCookie("XSRF-TOKEN");assertNotNull(csrf);
        String token=json.readTree(response.getContentAsString()).get("csrfToken").asText();
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .cookie(csrf).header("X-XSRF-TOKEN",token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .header("X-XSRF-TOKEN",token)).andExpect(status().isForbidden());
    }

    @Test void bothExactFrontendOriginsAllowCredentialedPreflightButOtherOriginsFail() throws Exception {
        for(String origin:List.of(ORIGIN,"https://savanna-frontend.vercel.app")) {
            mvc.perform(options("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,"content-type,x-xsrf-token"))
                    .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"));
        }
        mvc.perform(options("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,"http://unlisted.example.invalid")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")).andExpect(status().isForbidden());
    }

    @Test void rejectsNoneWithoutSecureRatherThanEmittingBrowserRejectedCookie() {
        assertThrows(IllegalArgumentException.class,()->new AuthCookieFactory(false,"None",60000));
        assertThrows(IllegalArgumentException.class,()->new AuthCookieFactory(true,"unknown",60000));
    }
}

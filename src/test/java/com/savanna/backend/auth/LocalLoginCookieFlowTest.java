package com.savanna.backend.auth;

import com.savanna.backend.domain.User;
import com.savanna.backend.handler.OAuth2LoginSuccessHandler;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.RefreshTokenRepository;
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

/** Exercises real cookie issuance, CSRF checks, refresh rotation and JWT authentication in an isolated DB.
 * Does not claim to test Google sign-in or browser SameSite/third-party-cookie behavior. */
@SpringBootTest(properties = {
        "recommendation.ai.enabled=false",
        "app.auth.cookie.secure=false",
        "app.auth.cookie.same-site=Lax",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.oauth2.success-redirect-url=http://localhost:5173/login?google=success"
})
@Transactional
class LocalLoginCookieFlowTest {
    @Autowired WebApplicationContext context;
    @Autowired OAuth2LoginSuccessHandler successHandler;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JwtTokenProvider tokens;
    MockMvc mvc;
    private final JsonMapper json=JsonMapper.builder().build();
    private static final String ORIGIN="http://localhost:5173";

    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

    private Cookie login() throws Exception {
        String sub="local-cookie-test-"+UUID.randomUUID();
        users.saveAndFlush(new User(sub+"@example.invalid",sub));
        var principal=new DefaultOidcUser(List.of(),new OidcIdToken("test-id-token",Instant.now(),Instant.now().plusSeconds(60),Map.of("sub",sub)));
        var response=new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(),response,
                new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        assertEquals("http://localhost:5173/login?google=success",response.getRedirectedUrl());
        String header=response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Path=/api/v1/auth"));assertFalse(header.contains("Secure"));assertFalse(header.contains("Domain="));
        var cookie=response.getCookie("refreshToken");assertNotNull(cookie);
        assertTrue(tokens.isRefreshToken(cookie.getValue()));
        return cookie;
    }
    private record Csrf(Cookie cookie,String token) {}
    private Csrf csrf() throws Exception {
        var response=mvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN,ORIGIN))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"))
                .andReturn().getResponse();
        var cookie=response.getCookie("XSRF-TOKEN");assertNotNull(cookie);
        String token=json.readTree(response.getContentAsString()).get("csrfToken").asText();
        assertEquals(cookie.getValue(),token);return new Csrf(cookie,token);
    }
    @Test void loginCookieAndCsrfAllowRefreshRotationThenAuthenticatedMeAndLogout() throws Exception {
        var refresh=login();var csrf=csrf();
        var response=mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN,ORIGIN).cookie(refresh,csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"))
                .andReturn().getResponse();
        String access=json.readTree(response.getContentAsString()).get("data").get("accessToken").asText();
        assertTrue(tokens.isAccessToken(access));
        var rotated=response.getCookie("refreshToken");assertNotNull(rotated);assertNotEquals(refresh.getValue(),rotated.getValue());
        assertFalse(refreshTokens.findByTokenHash(tokens.hashToken(refresh.getValue())).orElseThrow().isActive());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.ORIGIN,ORIGIN).header(HttpHeaders.AUTHORIZATION,"Bearer "+access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.onboardingCompleted").value(false));
        var logout=mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN,ORIGIN).cookie(rotated,csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refreshToken");
        assertNotNull(logout);assertEquals(0,logout.getMaxAge());assertEquals("/api/v1/auth",logout.getPath());
    }
    @Test void refreshCookieWithoutCsrfIs403AndDoesNotRotateToken() throws Exception {
        var refresh=login();
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN).cookie(refresh))
                .andExpect(status().isForbidden());
        assertTrue(refreshTokens.findByTokenHash(tokens.hashToken(refresh.getValue())).orElseThrow().isActive());
    }
    @Test void validCsrfButMissingRefreshCookieIs401() throws Exception {
        var csrf=csrf();
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())).andExpect(status().isUnauthorized());
    }
    @Test void wrongCsrfOrMissingCsrfCookieIs403EvenWhenRefreshCookieExists() throws Exception {
        var refresh=login();var csrf=csrf();
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .cookie(refresh,csrf.cookie()).header("X-XSRF-TOKEN","wrong-test-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").servletPath("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .cookie(refresh).header("X-XSRF-TOKEN",csrf.token())).andExpect(status().isForbidden());
    }
    @Test void localCredentialedPreflightAllowsCsrfHeaderAndUnlistedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,"content-type,x-xsrf-token"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,"true"));
        mvc.perform(options("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN,"http://unlisted.example.invalid")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")).andExpect(status().isForbidden());
    }
}

package com.savanna.backend.auth;

import com.savanna.backend.domain.User;
import com.savanna.backend.handler.OAuth2LoginSuccessHandler;
import com.savanna.backend.handler.OAuth2LoginFailureHandler;
import com.savanna.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real embedded servlet cookie handling; Google token exchange and Vercel transport are not invoked. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "recommendation.ai.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=proxy-test-client",
        "spring.security.oauth2.client.registration.google.client-secret=proxy-test-secret",
        "app.cors.allowed-origins=http://localhost:5173,https://savanna-expedition.vercel.app,https://savanna-frontend.vercel.app"
})
@ActiveProfiles("prod")
class VercelProxyOAuthFlowTest {
    static final String FRONT="https://savanna-expedition.vercel.app";
    @Value("${local.server.port}") int port;
    @Autowired ClientRegistrationRepository registrations;
    @Autowired OAuth2LoginSuccessHandler success;
    @Autowired UserRepository users;
    @MockitoSpyBean OAuth2LoginFailureHandler failures;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    HttpRequest.Builder request(String path){return HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
            .timeout(Duration.ofSeconds(10)).header("X-Forwarded-Proto","https")
            .header("X-Forwarded-Host","savanna-expedition.vercel.app").header("X-Forwarded-Port","443");}
    HttpResponse<String> send(HttpRequest.Builder request)throws Exception{return http.send(request.build(),HttpResponse.BodyHandlers.ofString());}
    Map<String,String> query(URI uri){var map=new HashMap<String,String>();for(String part:uri.getRawQuery().split("&")){String[] pair=part.split("=",2);map.put(URLDecoder.decode(pair[0],java.nio.charset.StandardCharsets.UTF_8),URLDecoder.decode(pair[1],java.nio.charset.StandardCharsets.UTF_8));}return map;}
    String cookie(HttpResponse<?> response,String name){return response.headers().allValues("set-cookie").stream().filter(c->c.startsWith(name+"=")).findFirst().orElseThrow();}
    String pair(String cookie){return cookie.split(";",2)[0];}
    @Test void explicitCallbackAndHostOnlySecureSessionSurviveProxiedGoogleCancellation()throws Exception {
        assertEquals(FRONT+"/login/oauth2/code/google",registrations.findByRegistrationId("google").getRedirectUri());
        var start=send(request("/oauth2/authorization/google").GET());assertEquals(302,start.statusCode());
        var authorization=query(URI.create(start.headers().firstValue("location").orElseThrow()));
        assertEquals(FRONT+"/login/oauth2/code/google",authorization.get("redirect_uri"));
        String session=cookie(start,"JSESSIONID");String lower=session.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("secure"));assertTrue(lower.contains("httponly"));assertTrue(lower.contains("samesite=lax"));assertTrue(lower.contains("path=/"));assertFalse(lower.contains("domain="));
        var cancelled=send(request("/login/oauth2/code/google?error=access_denied&state="+URLEncoder.encode(authorization.get("state"),java.nio.charset.StandardCharsets.UTF_8)).header("Cookie",pair(session)).GET());
        assertEquals(302,cancelled.statusCode());assertEquals(FRONT+"/login?google=fail",cancelled.headers().firstValue("location").orElseThrow());
        assertTrue(cancelled.headers().firstValue("cache-control").orElseThrow().contains("no-store"));
        var error=org.mockito.ArgumentCaptor.forClass(org.springframework.security.core.AuthenticationException.class);
        verify(failures).onAuthenticationFailure(any(),any(),error.capture());
        assertEquals("access_denied",assertInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,error.getValue()).getError().getErrorCode());
    }
    @Test void untrustedHostCannotChangeExplicitCallbackOrFailureDestination()throws Exception {
        var start=send(request("/oauth2/authorization/google").setHeader("X-Forwarded-Host","unexpected.example.invalid").GET());
        var authorization=query(URI.create(start.headers().firstValue("location").orElseThrow()));
        assertEquals(FRONT+"/login/oauth2/code/google",authorization.get("redirect_uri"));
        var failure=send(request("/login/oauth2/code/google?error=access_denied&state=wrong-test-state").setHeader("X-Forwarded-Host","unexpected.example.invalid").GET());
        assertEquals(FRONT+"/login?google=fail",failure.headers().firstValue("location").orElseThrow());
    }
    @Test @Transactional
    void successSetsHostOnlyRefreshCookieAndFixedFrontendRedirect()throws Exception {
        String id="vercel-test-"+UUID.randomUUID();users.saveAndFlush(new User(id+"@example.invalid",id));
        var principal=new DefaultOidcUser(List.of(),new OidcIdToken("dummy-id-token",Instant.now(),Instant.now().plusSeconds(60),Map.of("sub",id)));
        var response=new MockHttpServletResponse();success.onAuthenticationSuccess(new MockHttpServletRequest(),response,new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        assertEquals(FRONT+"/login?google=success",response.getRedirectedUrl());
        var refresh=response.getCookie("refreshToken");assertNotNull(refresh);assertNull(refresh.getDomain());assertEquals("/api/v1/auth",refresh.getPath());assertTrue(refresh.getSecure());assertTrue(refresh.isHttpOnly());assertEquals("None",refresh.getAttribute("SameSite"));
        assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test void newOriginAllowsCredentialsAndCsrfStillProtectsRefresh()throws Exception {
        var csrf=send(request("/api/v1/auth/csrf").header("Origin",FRONT).GET());assertEquals(200,csrf.statusCode());
        // With forwarded host=frontend this is same-origin, so CORS headers are unnecessary.
        String cookie=cookie(csrf,"XSRF-TOKEN"),lower=cookie.toLowerCase(Locale.ROOT);assertFalse(lower.contains("domain="));assertFalse(lower.contains("httponly"));assertTrue(lower.contains("secure"));assertTrue(lower.contains("samesite=none"));assertTrue(lower.contains("path=/"));
        assertTrue(csrf.headers().firstValue("cache-control").orElseThrow().contains("no-store"));
        String token=JsonMapper.builder().build().readTree(csrf.body()).get("csrfToken").asText();
        var forbidden=send(request("/api/v1/auth/refresh").header("Origin",FRONT).POST(HttpRequest.BodyPublishers.noBody()));assertEquals(403,forbidden.statusCode());
        var missingRefresh=send(request("/api/v1/auth/refresh").header("Origin",FRONT).header("Cookie",pair(cookie)).header("X-XSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.noBody()));assertEquals(401,missingRefresh.statusCode());
        var preflight=send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/refresh")).timeout(Duration.ofSeconds(10))
                .header("Origin",FRONT).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-xsrf-token").method("OPTIONS",HttpRequest.BodyPublishers.noBody()));
        assertEquals(200,preflight.statusCode());assertEquals(FRONT,preflight.headers().firstValue("access-control-allow-origin").orElseThrow());assertEquals("true",preflight.headers().firstValue("access-control-allow-credentials").orElseThrow());
    }
}

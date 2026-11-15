package com.savanna.backend.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
    private final String failureRedirectUrl;
    public OAuth2LoginFailureHandler(@Value("${app.oauth2.failure-redirect-url:http://localhost:5173/login?google=fail}") String url) {
        this.failureRedirectUrl=url;
    }
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,AuthenticationException exception) throws IOException {
        response.setHeader("Cache-Control","no-store");
        // Do not put provider errors, codes, state or tokens in the redirect URL.
        response.sendRedirect(failureRedirectUrl);
    }
}

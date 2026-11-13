package com.savanna.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/** Shared cookie policy for OAuth success, token rotation/logout and CSRF issuance. */
@Component
public class AuthCookieFactory {
    private final boolean secure;
    private final String sameSite;
    private final long refreshExpiration;

    public AuthCookieFactory(
            @Value("${app.auth.cookie.secure:false}") boolean secure,
            @Value("${app.auth.cookie.same-site:Lax}") String sameSite,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secure=secure;
        this.sameSite=switch (sameSite.strip().toLowerCase(Locale.ROOT)) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> throw new IllegalArgumentException("Cookie SameSite must be Lax, Strict or None.");
        };
        if ("None".equals(this.sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure=true.");
        }
        this.refreshExpiration=refreshExpiration;
    }

    public ResponseCookie refreshToken(String value) {
        return refreshCookie(value,Duration.ofMillis(refreshExpiration));
    }

    public ResponseCookie clearRefreshToken() {
        return refreshCookie("",Duration.ZERO);
    }

    private ResponseCookie refreshCookie(String value,Duration maxAge) {
        return ResponseCookie.from("refreshToken",value)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path("/api/v1/auth").maxAge(maxAge).build();
    }

    public void customizeCsrfCookie(ResponseCookie.ResponseCookieBuilder builder) {
        builder.secure(secure).sameSite(sameSite);
    }
}

package com.savanna.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain localPublicSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http.securityMatcher(
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/api/v1/announcements",
                "/api/v1/announcements/*"
        );

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/announcements",
                        "/api/v1/announcements/*"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/announcements"
                ).permitAll()
                .anyRequest().denyAll()
        );
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/v1/announcements")
        );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain localFallbackSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http.authorizeHttpRequests(auth -> auth
                .anyRequest().denyAll()
        );

        return http.build();
    }
}
package com.savanna.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI apiDocumentation() {
        return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT")
                        .description("Access Token만 입력하세요. Bearer 접두사는 자동으로 붙습니다.")));
    }

    @Bean
    public OpenApiCustomizer apiBearerAuthentication() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")) {
                    item.readOperations().forEach(operation ->
                            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
                }
            });
        };
    }
}

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
                if (path.startsWith("/api/v1/") && !path.startsWith("/api/v1/auth/")
                        && !path.equals("/api/v1/invitations/{token}")) {
                    item.readOperations().forEach(operation ->
                            operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
                }
            });
        };
    }

    @Bean
    public OpenApiCustomizer membershipResponses() {
        return api -> {
            if(api.getPaths()==null)return;
            api.getPaths().forEach((path,item)-> {
                item.readOperations().forEach(operation -> {
                    if(operation.getResponses()==null)return;
                    // Explicit error annotations must not accidentally inherit the successful DTO.
                    operation.getResponses().forEach((status,response)-> {
                        if(path.startsWith("/api/v1/") && response.getContent()!=null && response.getContent().containsKey("*/*")) {
                            var json=response.getContent().remove("*/*");
                            response.getContent().putIfAbsent("application/json",json);
                        }
                        if(java.util.Set.of("400","404","409","410","413","415","503").contains(status))
                            response.setContent(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                                new io.swagger.v3.oas.models.media.MediaType().schema(
                                    new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ErrorResponse"))));
                    });
                });
                var operation=path.equals("/api/v1/teams") ? item.getPost()
                        : path.equals("/api/v1/invitations/{token}/accept") ? item.getPost()
                        : path.equals("/api/v1/proposal-recipients/{proposalRecipientId}/response") ? item.getPatch() : null;
                if(operation!=null) {
                    operation.getResponses().addApiResponse("409",new io.swagger.v3.oas.models.responses.ApiResponse()
                        .description("기존 팀 소속으로 생성·가입 불가(ALREADY_IN_TEAM). 같은 팀 초대 재수락도 409입니다.")
                        .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ErrorResponse"))
                                .example(java.util.Map.of("code","ALREADY_IN_TEAM","message",
                                    "이미 소속된 팀이 있습니다. 한 계정은 한 팀에만 소속될 수 있습니다.","errors",java.util.List.of())))));
                }
            });
        };
    }
}

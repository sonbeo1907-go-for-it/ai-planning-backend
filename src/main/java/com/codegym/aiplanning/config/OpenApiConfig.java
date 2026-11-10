package com.codegym.aiplanning.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String REFRESH_COOKIE = "refreshCookie";

    @Bean
    OpenAPI planningOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Planning Backend API")
                        .version("v1")
                        .description("Manual Planning Core API. Authentication uses 15-minute "
                                + "Bearer access tokens and a rotated HttpOnly refresh cookie."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSecuritySchemes(
                                REFRESH_COOKIE,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("refresh_token")
                                        .description("Rotated HttpOnly refresh token set by login "
                                                + "and refresh responses.")));
    }
}

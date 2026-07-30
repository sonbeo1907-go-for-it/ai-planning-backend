package com.codegym.aiplanning.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI planningOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Planning Backend API")
                        .version("1.0.0")
                        .description("API Documentation cho Hệ thống Quản lý Kế hoạch Học tập (AI Planning MVP). Hỗ trợ xác thực JWT Stateless, quản lý người dùng, kế hoạch ngày/tuần và theo dõi tiến độ.")
                        .contact(new Contact()
                                .name("CodeGym AI Planning Team")
                                .email("support@codegym.vn"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập Bearer Access Token thu được từ API /api/v1/auth/login")));
    }
}


package com.codegym.aiplanning.config;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.service.auth.impl.AccountEventsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AccountEventsWebSocketConfig implements WebSocketConfigurer {

    private final AccountEventsWebSocketHandler accountEventsHandler;
    private final CorsProperties corsProperties;

    public AccountEventsWebSocketConfig(
            AccountEventsWebSocketHandler accountEventsHandler,
            CorsProperties corsProperties) {
        this.accountEventsHandler = accountEventsHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(accountEventsHandler, ApiConstant.ACCOUNT_EVENTS)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }
}

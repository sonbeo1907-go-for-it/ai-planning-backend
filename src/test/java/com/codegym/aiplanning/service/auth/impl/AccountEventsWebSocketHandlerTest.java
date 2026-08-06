package com.codegym.aiplanning.service.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class AccountEventsWebSocketHandlerTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final AccountEventsWebSocketHandler handler =
            new AccountEventsWebSocketHandler(jwtDecoder, new ObjectMapper().findAndRegisterModules());

    @Test
    void authenticatedUserReceivesDeactivationEventAndConnectionCloses() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = openSession("socket-1");
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);

        handler.handleTextMessage(
                session,
                new TextMessage("{\"type\":\"AUTHENTICATE\",\"accessToken\":\"access-token\"}"));
        handler.accountDeactivated(userId);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().get(0).getPayload())
                .contains("\"type\":\"AUTHENTICATED\"");
        assertThat(messages.getAllValues().get(1).getPayload())
                .contains("\"type\":\"ACCOUNT_DEACTIVATED\"")
                .contains("\"code\":\"ADMIN_DEACTIVATED_ACCOUNT\"");
        verify(session).close(new CloseStatus(4001, "Account deactivated"));
    }

    @Test
    void invalidAccessTokenClosesConnectionWithoutAuthenticatingIt() throws Exception {
        WebSocketSession session = openSession("socket-2");
        when(jwtDecoder.decode("invalid-token")).thenThrow(new JwtException("invalid"));

        handler.handleTextMessage(
                session,
                new TextMessage("{\"type\":\"AUTHENTICATE\",\"accessToken\":\"invalid-token\"}"));

        verify(session).close(new CloseStatus(4003, "Authentication failed"));
    }

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}

package com.codegym.aiplanning.service.auth.impl;

import com.codegym.aiplanning.service.auth.AccountSecurityNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AccountEventsWebSocketHandler extends TextWebSocketHandler
        implements AccountSecurityNotifier {

    private static final Logger log =
            LoggerFactory.getLogger(AccountEventsWebSocketHandler.class);
    private static final String AUTHENTICATE = "AUTHENTICATE";
    private static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    private static final CloseStatus INVALID_AUTHENTICATION =
            new CloseStatus(4003, "Authentication failed");
    private static final CloseStatus ACCOUNT_DEACTIVATED_CLOSE =
            new CloseStatus(4001, "Account deactivated");

    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, Set<WebSocketSession>> sessionsByUser =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> usersBySession = new ConcurrentHashMap<>();

    public AccountEventsWebSocketHandler(JwtDecoder jwtDecoder, ObjectMapper objectMapper) {
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        if (usersBySession.containsKey(session.getId())) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            if (!AUTHENTICATE.equals(payload.path("type").asText())) {
                close(session, INVALID_AUTHENTICATION);
                return;
            }

            String accessToken = payload.path("accessToken").asText();
            Jwt jwt = jwtDecoder.decode(accessToken);
            UUID userId = UUID.fromString(jwt.getSubject());
            usersBySession.put(session.getId(), userId);
            sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(session);
            send(session, new AccountEventMessage(
                    "AUTHENTICATED", null, "Account event channel authenticated.", Instant.now()));
        } catch (JwtException | IllegalArgumentException | IOException exception) {
            close(session, INVALID_AUTHENTICATION);
        }
    }

    @Override
    public void accountDeactivated(UUID userId) {
        Set<WebSocketSession> userSessions = sessionsByUser.remove(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }

        AccountEventMessage event = new AccountEventMessage(
                ACCOUNT_DEACTIVATED,
                "ADMIN_DEACTIVATED_ACCOUNT",
                "Your account has been deactivated.",
                Instant.now());
        userSessions.forEach(session -> {
            usersBySession.remove(session.getId());
            if (!session.isOpen()) {
                return;
            }
            try {
                send(session, event);
                close(session, ACCOUNT_DEACTIVATED_CLOSE);
            } catch (IOException exception) {
                log.debug("Unable to deliver account deactivation event", exception);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        removeSession(session);
        close(session, CloseStatus.SERVER_ERROR);
    }

    private void removeSession(WebSocketSession session) {
        UUID userId = usersBySession.remove(session.getId());
        if (userId == null) {
            return;
        }
        sessionsByUser.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private void send(WebSocketSession session, AccountEventMessage event) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            }
        }
    }

    private void close(WebSocketSession session, CloseStatus status) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.close(status);
            }
        }
    }

    private record AccountEventMessage(
            String type,
            String code,
            String message,
            Instant timestamp) {}
}

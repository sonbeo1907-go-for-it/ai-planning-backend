package com.codegym.aiplanning.config;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String EXPIRED_TOKEN_VALIDATION_CODE = "token_expired";

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException)
            throws IOException {
        if (hasExpiredToken(authenticationException)) {
            write(
                    request,
                    response,
                    ErrorCode.SESSION_EXPIRED,
                    "Your session has expired. Please sign in again.");
            return;
        }
        write(request, response, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        write(
                request,
                response,
                ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String message)
            throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = resolveRequestId(request, response);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(
                        Instant.now(),
                        errorCode.status().value(),
                        errorCode.name(),
                        message,
                        request.getRequestURI(),
                        requestId,
                        List.of()));
    }

    private String resolveRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        if (!StringUtils.hasText(requestId)) {
            requestId = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
            if (!StringUtils.hasText(requestId) || requestId.length() > 100) {
                requestId = UUID.randomUUID().toString();
            }
            response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
        return requestId;
    }

    private boolean hasExpiredToken(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JwtValidationException jwtValidationException
                    && jwtValidationException.getErrors().stream()
                            .anyMatch(error -> EXPIRED_TOKEN_VALIDATION_CODE.equals(error.getErrorCode()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.codegym.aiplanning.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED),
    REFRESH_IN_PROGRESS(HttpStatus.CONFLICT),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_MUST_BE_DIFFERENT(HttpStatus.BAD_REQUEST),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

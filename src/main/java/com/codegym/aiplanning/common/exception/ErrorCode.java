package com.codegym.aiplanning.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
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

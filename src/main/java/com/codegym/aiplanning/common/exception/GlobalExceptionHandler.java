package com.codegym.aiplanning.common.exception;

import com.codegym.aiplanning.common.api.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusinessException(BusinessException exception) {
        return build(
                exception.errorCode().status(),
                exception.errorCode().name(),
                exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception) {
        return build(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_CREDENTIALS.name(),
                "Invalid username or password.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return build(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED.name(),
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Database constraint violation", exception);
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT.name(),
                "The operation conflicts with existing data.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        ApiError error = new ApiError(status.value(), code, message);
        return ResponseEntity.status(status).body(error);
    }
}

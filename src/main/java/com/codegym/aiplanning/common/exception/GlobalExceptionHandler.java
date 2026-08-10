package com.codegym.aiplanning.common.exception;

import com.codegym.aiplanning.common.api.ApiError;
import com.codegym.aiplanning.common.api.FieldViolation;
import com.codegym.aiplanning.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        return build(
                exception.errorCode().status(),
                exception.errorCode().name(),
                exception.getMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed.",
                request,
                violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed.",
                request,
                violations);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method '" + exception.getMethod() + "' is not supported for this endpoint.",
                request,
                List.of());
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiError> handleMalformedRequest(
            Exception exception, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed.",
                request,
                List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.INVALID_CREDENTIALS.name(),
                "Invalid email or password.",
                request,
                List.of());
    }

    @ExceptionHandler(AccountDisabledException.class)
    ResponseEntity<ApiError> handleAccountDisabled(
            AccountDisabledException exception, HttpServletRequest request) {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        ApiError error = new ApiError(
                Instant.now(),
                ErrorCode.ACCOUNT_DISABLED.status().value(),
                ErrorCode.ACCOUNT_DISABLED.name(),
                exception.getMessage(),
                request.getRequestURI(),
                requestId,
                exception.getReasonCode() != null ? exception.getReasonCode().name() : null,
                List.of());
        return ResponseEntity.status(ErrorCode.ACCOUNT_DISABLED.status()).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED.name(),
                "You do not have permission to perform this action.",
                request,
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Database constraint violation", exception);
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT.name(),
                "The operation conflicts with existing data.",
                request,
                List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLocking(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION.name(),
                "The resource was modified by another request. Reload it and try again.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error", exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred.",
                request,
                List.of());
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                requestId,
                null,
                violations);
        return ResponseEntity.status(status).body(error);
    }
}

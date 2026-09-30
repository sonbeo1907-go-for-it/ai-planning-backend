package com.codegym.aiplanning.common.api;

public record FieldViolation(String field, String code, String message) {

    public FieldViolation(String field, String message) {
        this(field, null, message);
    }
}

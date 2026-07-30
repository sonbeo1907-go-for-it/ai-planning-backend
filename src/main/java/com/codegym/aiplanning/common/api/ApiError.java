package com.codegym.aiplanning.common.api;

public record ApiError(int status, String code, String message) {}

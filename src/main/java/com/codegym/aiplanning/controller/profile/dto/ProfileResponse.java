package com.codegym.aiplanning.controller.profile.dto;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id, String username, String fullName, List<String> roles) {}

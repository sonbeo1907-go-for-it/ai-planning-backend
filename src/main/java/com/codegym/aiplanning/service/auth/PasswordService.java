package com.codegym.aiplanning.service.auth;

import com.codegym.aiplanning.controller.profile.dto.ChangePasswordRequest;
import java.util.UUID;

public interface PasswordService {

    void changePassword(UUID userId, UUID currentSessionId, ChangePasswordRequest request);
}

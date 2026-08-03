package com.codegym.aiplanning.service.admin;

import java.util.UUID;

public interface AdminUserService {

    void resetPassword(UUID userId, String newPassword);
}

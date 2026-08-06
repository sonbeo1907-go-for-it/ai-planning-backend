package com.codegym.aiplanning.service.auth;

import java.util.UUID;

public interface AccountSecurityNotifier {

    void accountDeactivated(UUID userId);
}

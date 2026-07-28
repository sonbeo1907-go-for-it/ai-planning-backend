package com.codegym.aiplanning.service.auth;

import com.codegym.aiplanning.service.auth.model.AuthToken;

public interface AuthService {

    AuthToken login(String username, String password);
}

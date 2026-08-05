package com.codegym.aiplanning.service.auth;

import com.codegym.aiplanning.service.auth.model.AuthResult;

public interface AuthService {

    AuthResult login(String email, String password);

    AuthResult loginWithGoogle(String idToken);

    AuthResult refresh(String refreshToken);

    void logout(String authorizationHeader, String refreshToken);
}

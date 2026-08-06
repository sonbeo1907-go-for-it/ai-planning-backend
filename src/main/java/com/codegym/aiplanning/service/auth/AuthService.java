package com.codegym.aiplanning.service.auth;

import com.codegym.aiplanning.service.auth.model.AuthResult;

public interface AuthService {

    void register(String email, String password, String fullName);

    AuthResult login(String email, String password);

    AuthResult loginWithGoogle(String idToken);

    AuthResult refresh(String refreshToken);

    void logout(String authorizationHeader, String refreshToken);
}

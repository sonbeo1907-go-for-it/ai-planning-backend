package com.codegym.aiplanning.service.auth;

public interface GoogleIdTokenVerifier {

    GoogleIdentityClaims verify(String idToken);

    record GoogleIdentityClaims(String subject, String email, String displayName) {}
}

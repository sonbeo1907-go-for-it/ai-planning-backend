package com.codegym.aiplanning.common.constant;

public final class ApiConstant {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String LOGIN = "/login";
    public static final String REGISTER = "/register";
    public static final String GOOGLE_LOGIN = "/google";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String PASSWORD_RESET_REQUEST = "/password-reset-request";
    public static final String PASSWORD_RESET = "/password-reset";
    public static final String PASSWORD = "/password";
    public static final String AUTH_LOGIN = AUTH + LOGIN;
    public static final String AUTH_REGISTER = AUTH + REGISTER;
    public static final String AUTH_GOOGLE_LOGIN = AUTH + GOOGLE_LOGIN;
    public static final String AUTH_REFRESH = AUTH + REFRESH;
    public static final String AUTH_LOGOUT = AUTH + LOGOUT;
    public static final String AUTH_PASSWORD_RESET_REQUEST = AUTH + PASSWORD_RESET_REQUEST;
    public static final String AUTH_PASSWORD_RESET = AUTH + PASSWORD_RESET;
    public static final String PROFILE = API_V1 + "/profile";
    public static final String PROFILE_SETUP = "/setup";
    public static final String ROADMAP_ONBOARDING = API_V1 + "/roadmap-onboarding";
    public static final String CURRENT = "/current";
    public static final String COMPLETE = "/complete";
    public static final String ROADMAP_ONBOARDING_BY_ID = "/{roadmapId}";
    public static final String ROADMAP_ONBOARDING_COMPLETE =
            ROADMAP_ONBOARDING_BY_ID + COMPLETE;
    public static final String ADMIN = API_V1 + "/admin";

    private ApiConstant() {}
}

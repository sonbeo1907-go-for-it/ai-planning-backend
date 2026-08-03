package com.codegym.aiplanning.common.constant;

public final class ApiConstant {

    public static final String API_V1 = "/api/v1";

    public static final String AUTH = API_V1 + "/auth";
    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String AUTH_LOGIN = AUTH + LOGIN;
    public static final String AUTH_REFRESH = AUTH + REFRESH;
    public static final String AUTH_LOGOUT = AUTH + LOGOUT;
    public static final String PROFILE = API_V1 + "/profile";
    public static final String ACCOUNT_EVENTS = "/ws/account-events";

    public static final String USERS = API_V1 + "/users";
    public static final String ADMIN_USERS = API_V1 + "/admin/users";
    public static final String CLASSES = API_V1 + "/classes";
    public static final String COURSES = API_V1 + "/courses";
    public static final String ENROLLMENTS = API_V1 + "/enrollments";
    public static final String INSTRUCTOR_ASSIGNMENTS = API_V1 + "/instructor-assignments";

    public static final String CURRICULUM_MODULES = API_V1 + "/curriculum/modules";
    public static final String CURRICULUM_RESOURCES = API_V1 + "/curriculum/resources";

    public static final String WEEKLY = API_V1 + "/weekly-plans";
    public static final String DAILY = API_V1 + "/daily-plans";
    public static final String PROGRESS = API_V1 + "/progress";
    public static final String REVIEWS = API_V1 + "/reviews";

    private ApiConstant() {}
}

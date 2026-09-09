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
    public static final String ADMIN_AI_PROVIDERS = ADMIN + "/ai-providers";
    public static final String AI_PROVIDER_BY_ID = "/{providerId}";
    public static final String AI_PROVIDER_ENABLE = AI_PROVIDER_BY_ID + "/enable";
    public static final String AI_PROVIDER_DISABLE = AI_PROVIDER_BY_ID + "/disable";
    public static final String AI_PROVIDER_CREDENTIALS = AI_PROVIDER_BY_ID + "/credentials";
    public static final String AI_PROVIDER_CREDENTIAL_BY_ID =
            AI_PROVIDER_CREDENTIALS + "/{credentialId}";
    public static final String AI_PROVIDER_CREDENTIAL_ENABLE =
            AI_PROVIDER_CREDENTIAL_BY_ID + "/enable";
    public static final String AI_PROVIDER_CREDENTIAL_DISABLE =
            AI_PROVIDER_CREDENTIAL_BY_ID + "/disable";
    public static final String ADMIN_AI_PROVIDER_CONFIGS = ADMIN + "/ai-provider-configs";
    public static final String AI_PROVIDER_CONFIG_BY_ID = "/{configId}";
    public static final String AI_PROVIDER_CONFIG_ENABLE =
            AI_PROVIDER_CONFIG_BY_ID + "/enable";
    public static final String AI_PROVIDER_CONFIG_DISABLE =
            AI_PROVIDER_CONFIG_BY_ID + "/disable";
    public static final String AI_PROVIDER_CONFIG_DEFAULT =
            AI_PROVIDER_CONFIG_BY_ID + "/default";
    public static final String AI_PROVIDER_TEST_CONNECTION =
            AI_PROVIDER_CONFIG_BY_ID + "/test-connection";
    public static final String DAILY_PLANS = API_V1 + "/daily-plans";
    public static final String DAILY_PLAN_TODAY = "/today";
    public static final String DAILY_PLAN_BY_ID = "/{planId}";
    public static final String DAILY_PLAN_VERSIONS = DAILY_PLAN_BY_ID + "/versions";
    public static final String DAILY_PLAN_VERSION_BY_ID =
            DAILY_PLAN_VERSIONS + "/{versionId}";
    public static final String DAILY_PLAN_VERSION_ACTIVATE =
            DAILY_PLAN_VERSION_BY_ID + "/activate";
    public static final String DAILY_PLAN_VERSION_ITEMS =
            DAILY_PLAN_VERSION_BY_ID + "/items";
    public static final String DAILY_PLAN_VERSION_ITEM_BY_ID =
            DAILY_PLAN_VERSION_ITEMS + "/{itemId}";
    public static final String DAILY_PLAN_ITEM_PROGRESS =
            DAILY_PLAN_BY_ID + "/items/{itemId}/progress";
    public static final String DAILY_PLAN_ITEM_POMODORO =
            DAILY_PLAN_BY_ID + "/items/{itemId}/pomodoro";
    public static final String DAILY_PLAN_GENERATE_AI =
            DAILY_PLAN_BY_ID + "/generate-ai";
    public static final String DAILY_PLAN_REGENERATE_AI =
            DAILY_PLAN_BY_ID + "/regenerate-ai";
    public static final String DAILY_PLAN_CURRENT_AI_EXECUTION =
            DAILY_PLAN_BY_ID + "/ai-executions/current";
    public static final String DAILY_PLAN_AVAILABLE_LEARNING_UNITS =
            DAILY_PLAN_BY_ID + "/available-learning-units";
    public static final String ROADMAPS = API_V1 + "/roadmaps";
    public static final String ROADMAP_BY_ID = "/{roadmapId}";
    public static final String ROADMAP_COPY = ROADMAP_BY_ID + "/copy";
    public static final String ROADMAP_VERSIONS = ROADMAP_BY_ID + "/versions";
    public static final String ROADMAP_VERSION_BY_ID =
            ROADMAP_VERSIONS + "/{versionId}";
    public static final String ROADMAP_VERSION_ACTIVATE =
            ROADMAP_VERSION_BY_ID + "/activate";
    public static final String ROADMAP_VERSION_MILESTONES =
            ROADMAP_VERSION_BY_ID + "/milestones";
    public static final String ROADMAP_VERSION_TOPICS =
            ROADMAP_VERSION_MILESTONES + "/{milestoneId}/topics";
    public static final String ROADMAP_VERSION_LEARNING_UNITS =
            ROADMAP_VERSION_BY_ID + "/topics/{topicId}/learning-units";
    public static final String ROADMAP_PROGRESS = ROADMAP_BY_ID + "/progress";
    public static final String ROADMAP_VERSION_ITEM_BY_ID =
            ROADMAP_VERSION_BY_ID + "/items/{itemId}";
    public static final String ROADMAP_GENERATE_AI = ROADMAP_BY_ID + "/generate-ai";
    public static final String ROADMAP_REGENERATE_AI = ROADMAP_BY_ID + "/regenerate-ai";
    public static final String ROADMAP_CURRENT_AI_EXECUTION =
            ROADMAP_BY_ID + "/ai-executions/current";
    public static final String ROADMAP_WEAK_TOPICS = ROADMAP_BY_ID + "/weak-topics";
    public static final String DAILY_PLAN_EVALUATION = DAILY_PLAN_BY_ID + "/evaluation";
    public static final String DAILY_PLAN_QUIZ_GENERATE = DAILY_PLAN_BY_ID + "/quiz/generate";
    public static final String DAILY_PLAN_QUIZ_CURRENT_EXECUTION =
            DAILY_PLAN_BY_ID + "/quiz/ai-executions/current";
    public static final String DAILY_PLAN_QUIZ_BY_ID = DAILY_PLAN_BY_ID + "/quiz/{quizId}";
    public static final String DAILY_PLAN_QUIZ_SUBMIT = DAILY_PLAN_QUIZ_BY_ID + "/submit";
    public static final String WEAK_TOPICS = API_V1 + "/weak-topics";
    public static final String WEAK_TOPIC_BY_ID = "/{weakTopicId}";
    public static final String WEAK_TOPIC_MASTERY_CHECK_GENERATE =
            WEAK_TOPIC_BY_ID + "/mastery-check/generate";
    public static final String WEAK_TOPIC_MASTERY_CHECK_SUBMIT =
            WEAK_TOPIC_BY_ID + "/mastery-check/{quizId}/submit";
    public static final String AI_EXECUTIONS = API_V1 + "/ai-executions";
    public static final String AI_EXECUTION_BY_ID = "/{executionId}";
    public static final String MATERIALS = API_V1 + "/materials";

    private ApiConstant() {}
}

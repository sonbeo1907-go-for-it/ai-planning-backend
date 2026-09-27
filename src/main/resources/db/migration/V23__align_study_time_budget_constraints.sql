ALTER TABLE user_profiles
    DROP CONSTRAINT ck_user_profiles_default_daily_minutes;

ALTER TABLE user_profiles
    ADD CONSTRAINT ck_user_profiles_default_daily_minutes
        CHECK (
            default_daily_minutes BETWEEN 15 AND 480
            AND MOD(default_daily_minutes, 15) = 0
        );

ALTER TABLE daily_plan_versions
    DROP CONSTRAINT ck_daily_plan_versions_available_minutes;

ALTER TABLE daily_plan_versions
    ADD CONSTRAINT ck_daily_plan_versions_available_minutes
        CHECK (
            available_minutes BETWEEN 15 AND 480
            AND MOD(available_minutes, 15) = 0
        );

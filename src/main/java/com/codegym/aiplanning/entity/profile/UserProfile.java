package com.codegym.aiplanning.entity.profile;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    public static final String DEFAULT_TIME_ZONE = "UTC";
    public static final String DEFAULT_LOCALE = "en";
    public static final int DEFAULT_DAILY_MINUTES = 60;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(name = "default_daily_minutes", nullable = false)
    private int defaultDailyMinutes;

    @Column(name = "learning_preferences", columnDefinition = "TEXT")
    private String learningPreferences;

    protected UserProfile() {}

    public static UserProfile create(UserAccount user) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        profile.timeZone = DEFAULT_TIME_ZONE;
        profile.locale = DEFAULT_LOCALE;
        profile.defaultDailyMinutes = DEFAULT_DAILY_MINUTES;
        return profile;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getLocale() {
        return locale;
    }

    public int getDefaultDailyMinutes() {
        return defaultDailyMinutes;
    }

    public String getLearningPreferences() {
        return learningPreferences;
    }

    public void update(
            String timeZone,
            String locale,
            Integer defaultDailyMinutes,
            String learningPreferences) {
        if (timeZone != null) {
            this.timeZone = timeZone;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (defaultDailyMinutes != null) {
            this.defaultDailyMinutes = defaultDailyMinutes;
        }
        if (learningPreferences != null) {
            this.learningPreferences = learningPreferences.isBlank()
                    ? null
                    : learningPreferences.trim();
        }
    }
}

package com.codegym.aiplanning.entity.profile;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    public static final String DEFAULT_TIME_ZONE = "UTC";
    public static final String DEFAULT_LOCALE = "en";
    public static final int DEFAULT_DAILY_MINUTES = 60;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(name = "default_daily_minutes", nullable = false)
    private int defaultDailyMinutes;

    @Column(name = "setup_completed_at")
    private Instant setupCompletedAt;

    protected UserProfile() {}

    public static UserProfile create(UserAccount user, String displayName) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        profile.displayName = displayName.trim();
        profile.timeZone = DEFAULT_TIME_ZONE;
        profile.locale = DEFAULT_LOCALE;
        profile.defaultDailyMinutes = DEFAULT_DAILY_MINUTES;
        return profile;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getDisplayName() {
        return displayName;
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

    public Instant getSetupCompletedAt() {
        return setupCompletedAt;
    }

    public boolean isSetupCompleted() {
        return setupCompletedAt != null;
    }

    public void update(
            String displayName,
            String timeZone,
            String locale,
            Integer defaultDailyMinutes) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (timeZone != null) {
            this.timeZone = timeZone;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (defaultDailyMinutes != null) {
            this.defaultDailyMinutes = defaultDailyMinutes;
        }
    }

    public void completeSetup(
            String displayName,
            String timeZone,
            String locale,
            Integer defaultDailyMinutes,
            Instant completedAt) {
        update(displayName, timeZone, locale, defaultDailyMinutes);
        if (setupCompletedAt == null) {
            setupCompletedAt = completedAt;
        }
    }
}

package com.codegym.aiplanning.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class GoogleIdentityMigrationTest {

    @Test
    void v6AllowsPasswordlessAccountAndEnforcesUniqueGoogleSubject() throws Exception {
        String databaseName = "google_identity_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrateTo(url, "6");

        UUID userId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO user_accounts (
                        id, username, email, password_hash, full_name, role, status,
                        failed_login_attempts, created_at, updated_at
                    ) VALUES (
                        '%s', 'google_test', 'google.test@example.com', NULL,
                        'Google Test', 'STUDENT', 'ACTIVE', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId));
            statement.executeUpdate("""
                    INSERT INTO auth_identities (
                        id, user_id, provider, provider_subject, provider_email,
                        created_at, updated_at
                    ) VALUES (
                        RANDOM_UUID(), '%s', 'GOOGLE', 'google-subject',
                        'google.test@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId));

            try (ResultSet result = statement.executeQuery(
                    "SELECT password_hash FROM user_accounts WHERE id = '" + userId + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("password_hash")).isNull();
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO auth_identities (
                        id, user_id, provider, provider_subject, provider_email,
                        created_at, updated_at
                    ) VALUES (
                        RANDOM_UUID(), '%s', 'GOOGLE', 'google-subject',
                        'google.test@example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(userId)))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    private void migrateTo(String url, String version) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }
}

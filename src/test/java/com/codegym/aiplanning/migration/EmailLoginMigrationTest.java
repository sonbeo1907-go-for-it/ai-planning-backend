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

class EmailLoginMigrationTest {

    @Test
    void v5BackfillsLegacyAccountsAndEnforcesUniqueEmail() throws Exception {
        String databaseName = "email_migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        migrateTo(url, "4");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO user_accounts (
                        id, username, password_hash, full_name, role, status,
                        created_at, updated_at
                    ) VALUES (
                        RANDOM_UUID(), 'Legacy_User', 'password-hash', 'Legacy User',
                        'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
        }

        migrateTo(url, "5");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT email FROM user_accounts WHERE username = 'Legacy_User'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("email")).isEqualTo("legacy_user@legacy.local");
        }

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO user_accounts (
                        id, username, email, password_hash, full_name, role, status,
                        created_at, updated_at
                    ) VALUES (
                        RANDOM_UUID(), 'another_user', 'legacy_user@legacy.local',
                        'password-hash', 'Another User', 'STUDENT', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """))
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

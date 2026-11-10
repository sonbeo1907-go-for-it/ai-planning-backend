package com.codegym.aiplanning.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class V30BillingMigrationTest {

    @Test
    @DisplayName("V30 migration creates AI credit rate and reservation tables and seeds initial credit rates")
    void v30Migration_createsTablesAndSeedsRates() throws Exception {
        String databaseName = "billing_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM ai_credit_rates WHERE status = 'ACTIVE'")) {
                assertThat(rs.next()).isTrue();
                int count = rs.getInt(1);
                assertThat(count).isGreaterThanOrEqualTo(6);
            }

            try (ResultSet rs = statement.executeQuery("SELECT credit_cost FROM ai_credit_rates WHERE purpose = 'ROADMAP_GENERATION'")) {
                assertThat(rs.next()).isTrue();
                long cost = rs.getLong(1);
                assertThat(cost).isEqualTo(10L);
            }

            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM ai_credit_reservations")) {
                assertThat(rs.next()).isTrue();
                int count = rs.getInt(1);
                assertThat(count).isEqualTo(0);
            }
        }
    }
}

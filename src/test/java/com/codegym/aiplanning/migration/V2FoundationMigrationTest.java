package com.codegym.aiplanning.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class V2FoundationMigrationTest {

    @Test
    void cleanSchemaContainsV2FoundationAndRoadmapOnboardingConstraints() throws Exception {
        String databaseName = "v2_foundation_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Set<String> tables = applicationTables(connection.getMetaData());
            assertThat(tables).contains(
                    "user_accounts",
                    "user_profiles",
                    "auth_sessions",
                    "refresh_tokens",
                    "auth_identities",
                    "password_reset_tokens",
                    "audit_logs",
                    "learning_sources",
                    "roadmaps",
                    "roadmap_sources",
                    "roadmap_versions",
                    "roadmap_items",
                    "ai_providers",
                    "ai_provider_credentials",
                    "ai_provider_configs");
            assertThat(tables).doesNotContain("courses", "modules", "classes");

            UUID userId = UUID.randomUUID();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(accountInsert(userId, "USER", "v2-user@example.com"));
                statement.executeUpdate("""
                        INSERT INTO user_profiles (
                            id, user_id, display_name, time_zone, locale, default_daily_minutes,
                            created_at, updated_at
                        ) VALUES (
                            RANDOM_UUID(), '%s', 'V2 User', 'UTC', 'en', 60,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(userId));

                assertThatThrownBy(() -> statement.executeUpdate(
                                accountInsert(UUID.randomUUID(), "STUDENT", "student@example.com")))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate(
                                accountInsert(UUID.randomUUID(), "INSTRUCTOR", "instructor@example.com")))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO user_profiles (
                                    id, user_id, display_name, time_zone, locale, default_daily_minutes,
                                    created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'Duplicate', 'UTC', 'en', 60,
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(userId)))
                        .isInstanceOf(java.sql.SQLException.class);

                UUID sourceId = UUID.randomUUID();
                UUID roadmapId = UUID.randomUUID();
                statement.executeUpdate("""
                        INSERT INTO learning_sources (
                            id, owner_id, source_type, status, content_text,
                            created_at, updated_at
                        ) VALUES (
                            '%s', '%s', 'GOAL', 'READY', 'Learn React',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(sourceId, userId));
                statement.executeUpdate("""
                        INSERT INTO roadmaps (
                            id, owner_id, status, proficiency_level,
                            daily_commitment_minutes, expected_duration_days,
                            onboarding_completed_at, created_at, updated_at
                        ) VALUES (
                            '%s', '%s', 'DRAFT', 'BEGINNER', 30, 30,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(roadmapId, userId));
                statement.executeUpdate("""
                        INSERT INTO roadmap_sources (
                            id, roadmap_id, learning_source_id, created_at, updated_at
                        ) VALUES (
                            RANDOM_UUID(), '%s', '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(roadmapId, sourceId));

                UUID roadmapVersionId = UUID.randomUUID();
                UUID milestoneId = UUID.randomUUID();
                statement.executeUpdate("""
                        INSERT INTO roadmap_versions (
                            id, roadmap_id, version_number, status, origin,
                            draft_slot_roadmap_id, created_at, updated_at
                        ) VALUES (
                            '%s', '%s', 1, 'DRAFT', 'MANUAL', '%s',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(roadmapVersionId, roadmapId, roadmapId));
                statement.executeUpdate("""
                        INSERT INTO roadmap_items (
                            id, roadmap_version_id, item_type, title, order_index,
                            created_at, updated_at
                        ) VALUES (
                            '%s', '%s', 'MILESTONE', 'Week 1', 0,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(milestoneId, roadmapVersionId));
                statement.executeUpdate("""
                        INSERT INTO roadmap_items (
                            id, roadmap_version_id, parent_item_id, item_type, title,
                            order_index, estimated_minutes, created_at, updated_at
                        ) VALUES (
                            RANDOM_UUID(), '%s', '%s', 'TOPIC', 'Java basics',
                            0, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(roadmapVersionId, milestoneId));

                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO roadmap_versions (
                                    id, roadmap_id, version_number, status, origin,
                                    draft_slot_roadmap_id, created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 2, 'DRAFT', 'USER_EDITED', '%s',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(roadmapId, roadmapId)))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO roadmap_items (
                                    id, roadmap_version_id, item_type, title, order_index,
                                    estimated_minutes, created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'TOPIC', 'Invalid topic', 0,
                                    30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(roadmapVersionId)))
                        .isInstanceOf(java.sql.SQLException.class);

                UUID onboardingRoadmapId = UUID.randomUUID();
                statement.executeUpdate("""
                        INSERT INTO roadmaps (
                            id, owner_id, status, onboarding_slot_owner_id,
                            created_at, updated_at
                        ) VALUES (
                            '%s', '%s', 'ONBOARDING', '%s',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(onboardingRoadmapId, userId, userId));

                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO roadmaps (
                                    id, owner_id, status, onboarding_slot_owner_id,
                                    created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'ONBOARDING', '%s',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(userId, userId)))
                        .isInstanceOf(java.sql.SQLException.class);

                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO roadmaps (
                                    id, owner_id, status, daily_commitment_minutes,
                                    created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'ONBOARDING', 45,
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(userId)))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO learning_sources (
                                    id, owner_id, source_type, status,
                                    created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'GOAL', 'READY',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(userId)))
                        .isInstanceOf(java.sql.SQLException.class);

                UUID deepSeekProviderId = UUID.randomUUID();
                statement.executeUpdate("""
                        INSERT INTO ai_providers (
                            id, code, display_name, base_url, protocol,
                            credential_strategy, enabled, created_at, updated_at
                        ) VALUES (
                            '%s', 'DEEPSEEK', 'DeepSeek', 'https://api.deepseek.com/v1',
                            'OPENAI_COMPATIBLE', 'PRIORITY', TRUE,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(deepSeekProviderId));
                statement.executeUpdate("""
                        INSERT INTO ai_provider_credentials (
                            id, provider_id, label, secret_ref, priority, enabled,
                            created_at, updated_at
                        ) VALUES (
                            RANDOM_UUID(), '%s', 'Primary', 'env:DEEPSEEK_API_KEY',
                            100, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(deepSeekProviderId));
                statement.executeUpdate("""
                        INSERT INTO ai_provider_configs (
                            id, provider_id, purpose, model,
                            enabled, default_provider, default_slot_purpose,
                            timeout_seconds, max_input_tokens, max_output_tokens,
                            temperature, created_at, updated_at
                        ) VALUES (
                            RANDOM_UUID(), '%s', 'ROADMAP_GENERATION',
                            'deepseek-chat', TRUE, TRUE,
                            'ROADMAP_GENERATION', 10, 100000, 8000, 0.2,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(deepSeekProviderId));

                UUID openAiProviderId = UUID.randomUUID();
                statement.executeUpdate("""
                        INSERT INTO ai_providers (
                            id, code, display_name, base_url, protocol,
                            credential_strategy, enabled, created_at, updated_at
                        ) VALUES (
                            '%s', 'OPENAI', 'OpenAI', 'https://api.openai.com/v1',
                            'OPENAI_COMPATIBLE', 'PRIORITY', TRUE,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """.formatted(openAiProviderId));
                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO ai_provider_configs (
                                    id, provider_id, purpose, model,
                                    enabled, default_provider, default_slot_purpose,
                                    timeout_seconds, max_input_tokens, max_output_tokens,
                                    temperature, created_at, updated_at
                                ) VALUES (
                                    RANDOM_UUID(), '%s', 'ROADMAP_GENERATION',
                                    'gpt-5.6-luna', TRUE, TRUE,
                                    'ROADMAP_GENERATION', 10, 100000, 8000, 0.2,
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                                )
                                """.formatted(openAiProviderId)))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    private Set<String> applicationTables(DatabaseMetaData metadata) throws Exception {
        Set<String> tables = new TreeSet<>();
        try (ResultSet result = metadata.getTables(null, "public", "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private String accountInsert(UUID id, String role, String email) {
        return """
                INSERT INTO user_accounts (
                    id, email, password_hash, role, status,
                    failed_login_attempts, created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'password-hash', '%s', 'ACTIVE',
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(id, email, role);
    }
}

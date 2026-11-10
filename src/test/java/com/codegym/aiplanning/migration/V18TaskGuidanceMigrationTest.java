package com.codegym.aiplanning.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class V18TaskGuidanceMigrationTest {

    @Test
    void guidanceSchemaEnforcesOwnershipRevisionAndReferenceInvariants()
            throws Exception {
        String databaseName = "task_guidance_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            Fixture fixture = insertFixture(statement);

            statement.executeUpdate(guidanceInsert(
                    fixture.guidanceId(),
                    fixture.ownerId(),
                    fixture.planId(),
                    fixture.planVersionId(),
                    fixture.planItemId()));
            statement.executeUpdate(executionInsert(
                    fixture.executionId(),
                    fixture.ownerId(),
                    fixture.providerConfigId(),
                    fixture.planItemId(),
                    fixture.revisionId()));
            statement.executeUpdate(revisionInsert(
                    fixture.revisionId(),
                    fixture.guidanceId(),
                    fixture.executionId(),
                    1));
            statement.executeUpdate(stepGuidanceInsert(
                    fixture.stepGuidanceId(),
                    fixture.revisionId(),
                    fixture.sourceStepId()));

            statement.executeUpdate(externalReferenceInsert(
                    fixture.revisionId(),
                    fixture.stepGuidanceId(),
                    0));

            assertThatThrownBy(() -> statement.executeUpdate(guidanceInsert(
                            UUID.randomUUID(),
                            fixture.ownerId(),
                            fixture.planId(),
                            fixture.planVersionId(),
                            fixture.planItemId())))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate(guidanceInsert(
                            UUID.randomUUID(),
                            fixture.otherOwnerId(),
                            fixture.planId(),
                            fixture.planVersionId(),
                            fixture.secondPlanItemId())))
                    .isInstanceOf(SQLException.class);

            UUID secondExecutionId = UUID.randomUUID();
            UUID secondRevisionId = UUID.randomUUID();
            statement.executeUpdate(executionInsert(
                    secondExecutionId,
                    fixture.ownerId(),
                    fixture.providerConfigId(),
                    fixture.planItemId(),
                    secondRevisionId));
            assertThatThrownBy(() -> statement.executeUpdate(revisionInsert(
                            secondRevisionId,
                            fixture.guidanceId(),
                            secondExecutionId,
                            2)))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                            INSERT INTO task_guidance_references (
                                id, task_guidance_revision_id, provenance,
                                display_label, material_id, external_url,
                                order_index, created_at, updated_at
                            ) VALUES (
                                RANDOM_UUID(), '%s', 'MATERIAL',
                                'Invalid mixed target', '%s', 'https://example.com',
                                1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                            )
                            """.formatted(
                            fixture.revisionId(),
                            fixture.materialId())))
                    .isInstanceOf(SQLException.class);
        }
    }

    private Fixture insertFixture(Statement statement) throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        UUID planItemId = UUID.randomUUID();
        UUID secondPlanItemId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID providerConfigId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        statement.executeUpdate(accountInsert(ownerId, "guidance-owner@example.com"));
        statement.executeUpdate(accountInsert(otherOwnerId, "other-owner@example.com"));
        statement.executeUpdate("""
                INSERT INTO daily_plans (
                    id, user_id, plan_date, time_zone_snapshot, status,
                    created_at, updated_at
                ) VALUES (
                    '%s', '%s', DATE '2026-09-15', 'Asia/Ho_Chi_Minh', 'DRAFT',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(planId, ownerId));
        statement.executeUpdate("""
                INSERT INTO daily_plan_versions (
                    id, daily_plan_id, version_number, status, origin,
                    available_minutes, total_planned_minutes,
                    draft_slot_daily_plan_id, created_at, updated_at
                ) VALUES (
                    '%s', '%s', 1, 'DRAFT', 'MANUAL',
                    60, 30, '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(planVersionId, planId, planId));
        statement.executeUpdate("""
                INSERT INTO daily_plan_items (
                    id, daily_plan_version_id, category, title,
                    planned_minutes, order_index, status,
                    created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'PRACTICE', 'Implement one focused example',
                    30, 0, 'NOT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(planItemId, planVersionId));
        statement.executeUpdate("""
                INSERT INTO daily_plan_items (
                    id, daily_plan_version_id, category, title,
                    planned_minutes, order_index, status,
                    created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'PRACTICE', 'Second owned item',
                    30, 1, 'NOT_STARTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(secondPlanItemId, planVersionId));
        statement.executeUpdate("""
                INSERT INTO ai_providers (
                    id, code, display_name, base_url, protocol,
                    credential_strategy, enabled, created_at, updated_at
                ) VALUES (
                    '%s', 'DEEPSEEK', 'DeepSeek', 'https://api.deepseek.com/v1',
                    'OPENAI_COMPATIBLE', 'PRIORITY', TRUE,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(providerId));
        statement.executeUpdate("""
                INSERT INTO ai_provider_configs (
                    id, provider_id, purpose, model, enabled,
                    default_provider, default_slot_purpose,
                    timeout_seconds, max_input_tokens, max_output_tokens,
                    temperature, created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'TASK_GUIDANCE_GENERATION', 'deepseek-chat', TRUE,
                    TRUE, 'TASK_GUIDANCE_GENERATION',
                    90, 100000, 4000, 0.2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(providerConfigId, providerId));
        statement.executeUpdate("""
                INSERT INTO materials (
                    id, user_id, type, status, content,
                    created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'TEXT', 'READY', 'Reference content',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(materialId, ownerId));

        return new Fixture(
                ownerId,
                otherOwnerId,
                planId,
                planVersionId,
                planItemId,
                secondPlanItemId,
                providerConfigId,
                materialId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private String accountInsert(UUID id, String email) {
        return """
                INSERT INTO user_accounts (
                    id, email, password_hash, role, status,
                    failed_login_attempts, created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'password-hash', 'USER', 'ACTIVE',
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(id, email);
    }

    private String guidanceInsert(
            UUID guidanceId,
            UUID ownerId,
            UUID planId,
            UUID versionId,
            UUID itemId) {
        return """
                INSERT INTO task_guidances (
                    id, owner_id, daily_plan_id, daily_plan_version_id,
                    daily_plan_item_id, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', '%s', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(guidanceId, ownerId, planId, versionId, itemId);
    }

    private String executionInsert(
            UUID executionId,
            UUID ownerId,
            UUID providerConfigId,
            UUID targetId,
            UUID resultId) {
        return """
                INSERT INTO ai_executions (
                    id, owner_id, provider_config_id, purpose, operation,
                    target_type, target_id, status, result_type, result_id,
                    attempt_count, completed_at, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', 'TASK_GUIDANCE_GENERATION', 'GENERATE',
                    'DAILY_PLAN_ITEM', '%s', 'SUCCEEDED',
                    'TASK_GUIDANCE_REVISION', '%s', 1,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(
                executionId,
                ownerId,
                providerConfigId,
                targetId,
                resultId);
    }

    private String revisionInsert(
            UUID revisionId,
            UUID guidanceId,
            UUID executionId,
            int revisionNumber) {
        return """
                INSERT INTO task_guidance_revisions (
                    id, task_guidance_id, ai_execution_id, revision_number,
                    status, objective, task_summary,
                    daily_plan_item_entity_version, context_fingerprint,
                    draft_slot_guidance_id, generated_at,
                    created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', %d,
                    'DRAFT', 'Complete the task', 'Follow the planned steps',
                    0, '%s', '%s', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(
                revisionId,
                guidanceId,
                executionId,
                revisionNumber,
                "a".repeat(64),
                guidanceId);
    }

    private String stepGuidanceInsert(
            UUID stepGuidanceId,
            UUID revisionId,
            UUID sourceStepId) {
        return """
                INSERT INTO task_step_guidances (
                    id, task_guidance_revision_id, source_task_step_id,
                    task_step_entity_version, order_index,
                    instructions, expected_result, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', 0, 0,
                    'Run the focused example', 'The example succeeds',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(stepGuidanceId, revisionId, sourceStepId);
    }

    private String externalReferenceInsert(
            UUID revisionId,
            UUID stepGuidanceId,
            int orderIndex) {
        return """
                INSERT INTO task_guidance_references (
                    id, task_guidance_revision_id, task_step_guidance_id,
                    provenance, display_label, external_url, order_index,
                    created_at, updated_at
                ) VALUES (
                    RANDOM_UUID(), '%s', '%s', 'UNVERIFIED_EXTERNAL',
                    'External suggestion', 'https://example.com/reference', %d,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(revisionId, stepGuidanceId, orderIndex);
    }

    private record Fixture(
            UUID ownerId,
            UUID otherOwnerId,
            UUID planId,
            UUID planVersionId,
            UUID planItemId,
            UUID secondPlanItemId,
            UUID providerConfigId,
            UUID materialId,
            UUID guidanceId,
            UUID executionId,
            UUID revisionId,
            UUID stepGuidanceId,
            UUID sourceStepId) {}
}

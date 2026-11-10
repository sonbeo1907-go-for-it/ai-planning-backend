package com.codegym.aiplanning.entity.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskGuidanceRevisionTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void draftStartsAsTheCurrentImmutableGeneratedRevision() {
        Instant generatedAt = Instant.parse("2026-09-15T08:00:00Z");

        TaskGuidanceRevision revision = TaskGuidanceRevision.draft(
                guidance(),
                UUID.randomUUID(),
                1,
                "  Complete the exercise  ",
                "  Follow each persisted Task Step.  ",
                3L,
                FINGERPRINT,
                generatedAt);

        assertThat(revision.getStatus()).isEqualTo(TaskGuidanceRevisionStatus.DRAFT);
        assertThat(revision.isDraft()).isTrue();
        assertThat(revision.getObjective()).isEqualTo("Complete the exercise");
        assertThat(revision.getTaskSummary())
                .isEqualTo("Follow each persisted Task Step.");
        assertThat(revision.getDailyPlanItemEntityVersion()).isEqualTo(3L);
        assertThat(revision.getContextFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(revision.getGeneratedAt()).isEqualTo(generatedAt);
    }

    @Test
    void supersedeAndArchivePreserveAControlledRevisionLifecycle() {
        TaskGuidanceRevision revision = revision();

        revision.supersede();
        assertThat(revision.getStatus())
                .isEqualTo(TaskGuidanceRevisionStatus.SUPERSEDED);
        assertThat(revision.isDraft()).isFalse();

        revision.archive();
        revision.archive();
        assertThat(revision.getStatus())
                .isEqualTo(TaskGuidanceRevisionStatus.ARCHIVED);
    }

    @Test
    void aNonDraftRevisionCannotBeSupersededAgain() {
        TaskGuidanceRevision revision = revision();
        revision.supersede();

        assertThatThrownBy(revision::supersede)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only a DRAFT");
    }

    @Test
    void draftRejectsInvalidRevisionMetadata() {
        assertThatThrownBy(() -> TaskGuidanceRevision.draft(
                        guidance(),
                        UUID.randomUUID(),
                        0,
                        "Objective",
                        "Summary",
                        0L,
                        FINGERPRINT,
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revisionNumber");

        assertThatThrownBy(() -> TaskGuidanceRevision.draft(
                        guidance(),
                        UUID.randomUUID(),
                        1,
                        "Objective",
                        "Summary",
                        0L,
                        "not-a-fingerprint",
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
    }

    private TaskGuidanceRevision revision() {
        return TaskGuidanceRevision.draft(
                guidance(),
                UUID.randomUUID(),
                1,
                "Objective",
                "Summary",
                0L,
                FINGERPRINT,
                Instant.now());
    }

    private TaskGuidance guidance() {
        UserAccount owner = UserAccount.create(
                "revision@example.com",
                "password-hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        return TaskGuidance.create(
                owner,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}

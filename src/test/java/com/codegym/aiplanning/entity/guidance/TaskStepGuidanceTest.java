package com.codegym.aiplanning.entity.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStepGuidanceTest {

    @Test
    void createCapturesTheImmutableTaskStepSnapshot() {
        TaskGuidanceRevision revision = revision();
        UUID stepId = UUID.randomUUID();

        TaskStepGuidance guidance = TaskStepGuidance.create(
                revision,
                stepId,
                4L,
                0,
                "  Run the focused test.  ",
                "  The test passes.  ",
                "  Start with one scenario.  ",
                null,
                "  Prepare the fixture.  ");

        assertThat(guidance.getRevision()).isSameAs(revision);
        assertThat(guidance.getSourceTaskStepId()).isEqualTo(stepId);
        assertThat(guidance.getTaskStepEntityVersion()).isEqualTo(4L);
        assertThat(guidance.getOrderIndex()).isZero();
        assertThat(guidance.getInstructions()).isEqualTo("Run the focused test.");
        assertThat(guidance.getExpectedResult()).isEqualTo("The test passes.");
        assertThat(guidance.getTips()).isEqualTo("Start with one scenario.");
        assertThat(guidance.getCautions()).isNull();
        assertThat(guidance.getPrerequisites()).isEqualTo("Prepare the fixture.");
    }

    @Test
    void createRejectsMissingContentAndNegativeSnapshotValues() {
        assertThatThrownBy(() -> TaskStepGuidance.create(
                        revision(),
                        UUID.randomUUID(),
                        -1L,
                        0,
                        "Instructions",
                        "Expected result",
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskStepEntityVersion");

        assertThatThrownBy(() -> TaskStepGuidance.create(
                        revision(),
                        UUID.randomUUID(),
                        0L,
                        0,
                        " ",
                        "Expected result",
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");
    }

    private TaskGuidanceRevision revision() {
        UserAccount owner = UserAccount.create(
                "step-guidance@example.com",
                "password-hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        TaskGuidance root = TaskGuidance.create(
                owner,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
        return TaskGuidanceRevision.draft(
                root,
                UUID.randomUUID(),
                1,
                "Objective",
                "Summary",
                0L,
                "b".repeat(64),
                Instant.now());
    }
}

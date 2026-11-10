package com.codegym.aiplanning.entity.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskGuidanceTest {

    @Test
    void createCapturesExactOwnerAndDailyPlanPath() {
        UserAccount owner = user();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        TaskGuidance guidance = TaskGuidance.create(
                owner,
                planId,
                versionId,
                itemId);

        assertThat(guidance.getOwner()).isSameAs(owner);
        assertThat(guidance.getDailyPlanId()).isEqualTo(planId);
        assertThat(guidance.getDailyPlanVersionId()).isEqualTo(versionId);
        assertThat(guidance.getDailyPlanItemId()).isEqualTo(itemId);
    }

    @Test
    void createRejectsAnIncompleteResourcePath() {
        assertThatThrownBy(() -> TaskGuidance.create(
                        user(),
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dailyPlanVersionId");
    }

    private UserAccount user() {
        return UserAccount.create(
                "guidance@example.com",
                "password-hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
    }
}

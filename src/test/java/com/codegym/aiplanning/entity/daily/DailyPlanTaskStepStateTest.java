package com.codegym.aiplanning.entity.daily;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyPlanTaskStepStateTest {

    @Test
    void completionIsIdempotentAndUncheckingClearsCompletedAt() {
        DailyPlanTaskStepState state = DailyPlanTaskStepState.create(UUID.randomUUID());
        Instant firstCompletion = Instant.parse("2026-09-14T08:00:00Z");

        state.setCompleted(true, firstCompletion);
        state.setCompleted(true, firstCompletion.plusSeconds(60));

        assertThat(state.getCompleted()).isTrue();
        assertThat(state.getCompletedAt()).isEqualTo(firstCompletion);

        state.setCompleted(false, firstCompletion.plusSeconds(120));

        assertThat(state.getCompleted()).isFalse();
        assertThat(state.getCompletedAt()).isNull();
    }
}


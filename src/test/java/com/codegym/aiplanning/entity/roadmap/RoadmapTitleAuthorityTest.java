package com.codegym.aiplanning.entity.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.auth.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RoadmapTitleAuthorityTest {

    private final UserAccount owner = Mockito.mock(UserAccount.class);

    @Test
    void aiCannotOverwriteAUserAuthoredOnboardingTitle() {
        Roadmap roadmap = Roadmap.beginOnboarding(owner);
        roadmap.updateOnboardingTitle("Backend Java");
        roadmap.completeOnboarding(Instant.now());

        boolean changed = roadmap.applyAiSuggestedTitle("AI Backend Roadmap");

        assertThat(changed).isFalse();
        assertThat(roadmap.getTitle()).isEqualTo("Backend Java");
        assertThat(roadmap.getTitleOrigin()).isEqualTo(RoadmapTitleOrigin.USER);
    }

    @Test
    void firstAiSuggestionMayReplaceAGoalDerivedTitleOnlyOnce() {
        Roadmap roadmap = Roadmap.beginOnboarding(owner);
        roadmap.applyOnboardingGeneratedTitle(
                "Learn backend development", RoadmapTitleOrigin.GOAL_DERIVED);
        roadmap.completeOnboarding(Instant.now());

        assertThat(roadmap.applyAiSuggestedTitle("Lộ trình Backend Java"))
                .isTrue();
        assertThat(roadmap.applyAiSuggestedTitle("Tên từ lần tái tạo"))
                .isFalse();
        assertThat(roadmap.getTitle()).isEqualTo("Lộ trình Backend Java");
        assertThat(roadmap.getTitleOrigin())
                .isEqualTo(RoadmapTitleOrigin.AI_SUGGESTED);
    }

    @Test
    void manualMetadataIsAlwaysUserAuthoritative() {
        Roadmap roadmap = Roadmap.manualDraft(owner, "Manual Roadmap", null);

        roadmap.updateMetadata("Renamed Roadmap", "Description");

        assertThat(roadmap.getTitle()).isEqualTo("Renamed Roadmap");
        assertThat(roadmap.getTitleOrigin()).isEqualTo(RoadmapTitleOrigin.USER);
    }
}

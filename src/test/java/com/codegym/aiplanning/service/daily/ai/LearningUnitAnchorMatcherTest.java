package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearningUnitAnchorMatcherTest {

    private final LearningUnitAnchorMatcher matcher = new LearningUnitAnchorMatcher();

    @Test
    void matchesVietnamesePhraseAcrossAccentAndPunctuationVariations() {
        assertThat(matcher.belongsToLearningUnit(
                        "xac thuc JWT", "Thực hành xác thực JWT", null))
                .isTrue();
        assertThat(matcher.usedInStep(
                        "xác thực JWT", "Viết test xác-thực JWT", null))
                .isTrue();
    }

    @Test
    void neverMatchesInsideAnotherWordOrAcrossSourceFields() {
        assertThat(matcher.usedInStep("API", "Implement GraphAPI", null)).isFalse();
        assertThat(matcher.belongsToLearningUnit(
                        "JWT Spring", "Authenticate JWT", "Spring Security"))
                .isFalse();
    }

    @Test
    void rejectsTrivialAnchorAndDoesNotRepairUnrelatedStep() {
        assertThat(matcher.validLength("a")).isFalse();
        assertThat(matcher.repairAnchor(
                        "Spring", "Implement Kubernetes deployment", null,
                        "Use Spring Security", "Configure authentication filters"))
                .isEqualTo("Spring");
    }

    @Test
    void repairsOnlyMetadataUsingMeaningfulUnitWordPresentInStep() {
        assertThat(matcher.repairAnchor(
                        "Kubernetes", "Write Optional unit tests", null,
                        "Use Optional to avoid NullPointerException", null))
                .isEqualTo("Optional");
        assertThat(matcher.promptCandidates(
                        "Use Optional to avoid NullPointerException", null))
                .contains("Optional", "NullPointerException")
                .doesNotContain("Use", "to");
    }

    @Test
    void genericVietnameseVerbCannotGroundAnUnrelatedStep() {
        assertThat(matcher.validLength("Lập")).isFalse();
        assertThat(matcher.promptCandidates("Lập trình Java", null))
                .containsExactly("Java");
        assertThat(matcher.repairAnchor(
                        "Lập", "Lập dự án Kotlin", null,
                        "Lập trình Java", null))
                .isEqualTo("Lập");
    }
}

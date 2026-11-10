package com.codegym.aiplanning.entity.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskGuidanceReferenceTest {

    @Test
    void factoriesCreateDistinctProvenanceShapes() {
        TaskGuidanceRevision revision = revision("c");
        UUID materialId = UUID.randomUUID();

        TaskGuidanceReference material = TaskGuidanceReference.material(
                revision,
                null,
                materialId,
                "  Personal document  ",
                "  Section 2  ",
                0);
        TaskGuidanceReference external = TaskGuidanceReference.unverifiedExternal(
                revision,
                null,
                "https://example.com/reference",
                "External suggestion",
                1);

        assertThat(material.getProvenance())
                .isEqualTo(GuidanceReferenceProvenance.MATERIAL);
        assertThat(material.getMaterialId()).isEqualTo(materialId);
        assertThat(material.getDisplayLabel()).isEqualTo("Personal document");
        assertThat(material.getLocator()).isEqualTo("Section 2");
        assertThat(material.isUnverified()).isFalse();

        assertThat(external.getProvenance())
                .isEqualTo(GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL);
        assertThat(external.getExternalUrl())
                .isEqualTo("https://example.com/reference");
        assertThat(external.isUnverified()).isTrue();
    }

    @Test
    void stepLevelReferenceMustUseTheSameRevision() {
        TaskGuidanceRevision firstRevision = revision("d");
        TaskGuidanceRevision secondRevision = revision("e");
        TaskStepGuidance stepGuidance = TaskStepGuidance.create(
                firstRevision,
                UUID.randomUUID(),
                0L,
                0,
                "Run the test",
                "The test passes",
                null,
                null,
                null);

        assertThatThrownBy(() -> TaskGuidanceReference.roadmapContext(
                        secondRevision,
                        stepGuidance,
                        UUID.randomUUID(),
                        "Learning Unit",
                        null,
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same revision");
    }

    private TaskGuidanceRevision revision(String fingerprintCharacter) {
        UserAccount owner = UserAccount.create(
                UUID.randomUUID() + "@example.com",
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
                fingerprintCharacter.repeat(64),
                Instant.now());
    }
}

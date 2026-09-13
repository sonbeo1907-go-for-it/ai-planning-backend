package com.codegym.aiplanning.entity.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RoadmapItemLineageTest {

    private RoadmapItem learningUnit;

    @BeforeEach
    void setUp() {
        UserAccount owner = UserAccount.create(
                "lineage-owner@example.com",
                "Password1",
                UserRole.USER,
                AccountStatus.ACTIVE);
        setId(owner, UUID.randomUUID());

        Roadmap roadmap = Roadmap.manualDraft(owner, "Backend", null);
        setId(roadmap, UUID.randomUUID());
        RoadmapVersion version = RoadmapVersion.draft(
                roadmap,
                1,
                RoadmapVersionOrigin.MANUAL);
        setId(version, UUID.randomUUID());

        RoadmapItem milestone = RoadmapItem.milestone(
                version,
                "Week 1",
                null,
                0);
        setId(milestone, UUID.randomUUID());
        RoadmapItem topic = RoadmapItem.topic(
                version,
                milestone,
                "Object-oriented programming",
                null,
                0,
                120);
        setId(topic, UUID.randomUUID());
        learningUnit = RoadmapItem.learningUnit(
                version,
                topic,
                "Encapsulation",
                "Protect object state",
                0,
                30);
        setId(learningUnit, UUID.randomUUID());
    }

    @Test
    void reorderingOrChangingDurationPreservesLearningIdentity() {
        UUID originalLineage = learningUnit.getLineageId();

        learningUnit.update(
                "Encapsulation",
                "Protect object state",
                2,
                45);

        assertThat(learningUnit.getLineageId()).isEqualTo(originalLineage);
    }

    @Test
    void normalizationOnlyChangesPreserveLearningIdentity() {
        UUID originalLineage = learningUnit.getLineageId();

        learningUnit.update(
                "  ENCAPSULATION  ",
                "  protect OBJECT state  ",
                0,
                30);

        assertThat(learningUnit.getLineageId()).isEqualTo(originalLineage);
    }

    @Test
    void titleChangeCreatesNewLearningIdentity() {
        UUID originalLineage = learningUnit.getLineageId();

        learningUnit.update(
                "Advanced polymorphism",
                "Protect object state",
                0,
                30);

        assertThat(learningUnit.getLineageId()).isNotEqualTo(originalLineage);
    }

    @Test
    void descriptionChangeCreatesNewLearningIdentity() {
        UUID originalLineage = learningUnit.getLineageId();

        learningUnit.update(
                "Encapsulation",
                "Design immutable aggregate boundaries",
                0,
                30);

        assertThat(learningUnit.getLineageId()).isNotEqualTo(originalLineage);
    }

    private void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}

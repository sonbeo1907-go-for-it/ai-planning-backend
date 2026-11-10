package com.codegym.aiplanning.entity.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeakTopicEligibilityTest {

    @Test
    void firstCheckStartsOnTheNextLocalDayAndSurvivesAFailedAttempt() {
        Roadmap roadmap = mock(Roadmap.class);
        RoadmapVersion version = mock(RoadmapVersion.class);
        RoadmapItem unit = mock(RoadmapItem.class);
        UUID roadmapId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(roadmap.getId()).thenReturn(roadmapId);
        when(version.getId()).thenReturn(versionId);
        when(version.getRoadmap()).thenReturn(roadmap);
        when(unit.getItemType()).thenReturn(RoadmapItemType.LEARNING_UNIT);
        when(unit.getRoadmapVersion()).thenReturn(version);

        WeakTopic topic = WeakTopic.create(
                mock(UserAccount.class),
                roadmap,
                version,
                unit,
                WeakTopicTrigger.LOW_RATING,
                null,
                2,
                Instant.parse("2026-09-20T18:00:00Z"),
                "Asia/Ho_Chi_Minh");

        assertEquals(LocalDate.of(2026, 9, 22), topic.getEligibleOn());
        assertFalse(topic.isEligibleForMastery(Instant.parse("2026-09-21T16:59:59Z")));
        assertTrue(topic.isEligibleForMastery(Instant.parse("2026-09-21T17:00:00Z")));

        topic.markInReview();
        topic.markMasteryFailed(new BigDecimal("60.00"));

        assertEquals(WeakTopicStatus.UNRESOLVED, topic.getStatus());
        assertEquals(LocalDate.of(2026, 9, 22), topic.getEligibleOn());
        assertTrue(topic.isEligibleForMastery(Instant.parse("2026-09-23T00:00:00Z")));

        topic.updateTrigger(
                WeakTopicTrigger.QUIZ_FAILED,
                new BigDecimal("40.00"),
                null,
                Instant.parse("2026-09-23T18:00:00Z"),
                "UTC");
        assertEquals(LocalDate.of(2026, 9, 22), topic.getEligibleOn());
        assertEquals("Asia/Ho_Chi_Minh", topic.getEligibilityZone());

        topic.markMastered(Instant.parse("2026-09-24T00:00:00Z"), new BigDecimal("80.00"));
        topic.updateTrigger(
                WeakTopicTrigger.LOW_RATING,
                null,
                1,
                Instant.parse("2026-09-24T18:00:00Z"),
                "UTC");
        assertEquals(LocalDate.of(2026, 9, 25), topic.getEligibleOn());
        assertEquals("UTC", topic.getEligibilityZone());
    }
}

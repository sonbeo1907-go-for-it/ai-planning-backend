package com.codegym.aiplanning.service.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.service.roadmap.impl.RoadmapProgressServiceImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoadmapProgressServiceImplTest {

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapItemProgressRepository progressRepository;

    @Mock
    private ProgressEntryRepository progressEntryRepository;

    private RoadmapProgressServiceImpl service;
    private Map<UUID, RoadmapItemProgress> storedProgress;
    private Map<UUID, List<ProgressEntry>> historyByLearningUnitId;

    @BeforeEach
    void setUp() {
        service = new RoadmapProgressServiceImpl(
                roadmapRepository,
                roadmapItemRepository,
                progressRepository,
                progressEntryRepository);
        storedProgress = new LinkedHashMap<>();
        historyByLearningUnitId = new LinkedHashMap<>();

        when(progressRepository.findByUserIdAndRoadmapItemId(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        storedProgress.get(invocation.getArgument(1, UUID.class))));
        when(progressRepository.findByUserIdAndRoadmapVersionId(any(), any()))
                .thenAnswer(invocation -> List.copyOf(storedProgress.values()));
        when(progressRepository.saveAndFlush(any(RoadmapItemProgress.class)))
                .thenAnswer(invocation -> {
                    RoadmapItemProgress progress = invocation.getArgument(0);
                    storedProgress.put(progress.getRoadmapItemId(), progress);
                    return progress;
                });
        when(progressEntryRepository
                        .findByUserIdAndLearningUnitIdOrderByRecordedAtAscIdAsc(
                                any(), any()))
                .thenAnswer(invocation -> historyByLearningUnitId.getOrDefault(
                        invocation.getArgument(1), List.of()));
    }

    @Test
    void completedLearningUnitsRollUpToTopicAndRoadmapProgress() {
        UUID userId = UUID.randomUUID();
        UserAccount owner = UserAccount.create(
                "owner@example.com",
                "hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", userId);

        Roadmap roadmap = Roadmap.manualDraft(owner, "Java", null);
        ReflectionTestUtils.setField(roadmap, "id", UUID.randomUUID());
        RoadmapVersion version = RoadmapVersion.draft(
                roadmap,
                1,
                RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        version.activate(Instant.parse("2026-09-01T00:00:00Z"));
        roadmap.activateVersion(version.getId());

        RoadmapItem milestone = RoadmapItem.milestone(version, "OOP", null, 0);
        ReflectionTestUtils.setField(milestone, "id", UUID.randomUUID());
        RoadmapItem topic = RoadmapItem.topic(
                version,
                milestone,
                "Learn four core OOP principles",
                null,
                0,
                120);
        ReflectionTestUtils.setField(topic, "id", UUID.randomUUID());

        List<RoadmapItem> learningUnits = List.of(
                learningUnit(version, topic, "Encapsulation", 0),
                learningUnit(version, topic, "Inheritance", 1),
                learningUnit(version, topic, "Abstraction", 2),
                learningUnit(version, topic, "Polymorphism", 3));
        for (RoadmapItem unit : learningUnits) {
            when(roadmapItemRepository.findOwnedById(unit.getId(), userId))
                    .thenReturn(Optional.of(unit));
        }
        when(roadmapItemRepository.findOwnedById(topic.getId(), userId))
                .thenReturn(Optional.of(topic));
        assertThatThrownBy(() -> service.recordOutcome(
                        userId,
                        topic.getId(),
                        completedEntry(userId),
                        ProgressEntryStatus.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Learning Unit");

        for (int index = 0; index < learningUnits.size(); index++) {
            ProgressEntry completedEntry = completedEntry(userId);
            historyByLearningUnitId.put(
                    learningUnits.get(index).getId(), List.of(completedEntry));
            service.recordOutcome(
                    userId,
                    learningUnits.get(index).getId(),
                    completedEntry,
                    ProgressEntryStatus.COMPLETED);
        }

        List<RoadmapItem> allItems = new ArrayList<>();
        allItems.add(milestone);
        allItems.add(topic);
        allItems.addAll(learningUnits);
        for (int index = 1; index < 20; index++) {
            RoadmapItem otherTopic = RoadmapItem.topic(
                    version,
                    milestone,
                    "Topic " + index,
                    null,
                    index,
                    30);
            ReflectionTestUtils.setField(otherTopic, "id", UUID.randomUUID());
            allItems.add(otherTopic);
        }

        when(roadmapRepository.findByIdAndOwnerId(roadmap.getId(), userId))
                .thenReturn(Optional.of(roadmap));
        when(roadmapItemRepository
                        .findAllByRoadmapVersionIds(List.of(version.getId())))
                .thenReturn(allItems);

        RoadmapProgressResponse response = service.getProgress(userId, roadmap.getId());

        assertThat(storedProgress).doesNotContainKey(topic.getId());
        assertThat(storedProgress.get(learningUnits.get(0).getId()).getStatus())
                .isEqualTo(RoadmapItemProgressStatus.COMPLETED);
        assertThat(response.completedTopics()).isEqualTo(1);
        assertThat(response.totalTopics()).isEqualTo(20);
        assertThat(response.completionPercentage()).isEqualTo(5.0);
        assertThat(response.topics().get(0).completedLearningUnits()).isEqualTo(4);
        assertThat(response.topics().get(0).completionPercentage()).isEqualTo(100);
    }

    private RoadmapItem learningUnit(
            RoadmapVersion version,
            RoadmapItem topic,
            String title,
            int orderIndex) {
        RoadmapItem unit = RoadmapItem.learningUnit(
                version,
                topic,
                title,
                null,
                orderIndex,
                30);
        ReflectionTestUtils.setField(unit, "id", UUID.randomUUID());
        return unit;
    }

    private ProgressEntry completedEntry(UUID userId) {
        ProgressEntry entry = ProgressEntry.create(
                userId,
                UUID.randomUUID(),
                ProgressEntryStatus.COMPLETED,
                30,
                100,
                null,
                2,
                5,
                null,
                null);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entry, "recordedAt", Instant.now());
        return entry;
    }
}

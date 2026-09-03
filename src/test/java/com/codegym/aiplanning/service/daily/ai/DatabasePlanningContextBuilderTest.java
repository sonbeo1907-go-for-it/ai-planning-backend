package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DatabasePlanningContextBuilderTest {

    @Mock
    private DailyPlanRepository dailyPlanRepository;
    @Mock
    private DailyPlanVersionRepository dailyPlanVersionRepository;
    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;
    @Mock
    private ProgressEntryRepository progressEntryRepository;
    @Mock
    private RoadmapRepository roadmapRepository;
    @Mock
    private RoadmapVersionRepository roadmapVersionRepository;
    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    private DatabasePlanningContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DatabasePlanningContextBuilder(
                dailyPlanRepository,
                dailyPlanVersionRepository,
                dailyPlanItemRepository,
                progressEntryRepository,
                roadmapRepository,
                roadmapVersionRepository,
                roadmapItemRepository);
    }

    @Test
    void buildContext_usesOwnerScopedActiveRoadmapProgressAndAvailableTime() {
        UUID userId = UUID.randomUUID();
        UUID targetPlanId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID activeRoadmapVersionId = UUID.randomUUID();
        UUID targetVersionId = UUID.randomUUID();
        UUID priorPlanId = UUID.randomUUID();
        UUID priorVersionId = UUID.randomUUID();
        UUID priorItemId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.of(2026, 8, 25);

        UserAccount user = UserAccount.create(
                "owner@example.com",
                "hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", userId);

        Roadmap roadmap = Roadmap.manualDraft(user, "Java Backend", null);
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        RoadmapVersion activeRoadmapVersion = RoadmapVersion.draft(
                roadmap,
                2,
                RoadmapVersionOrigin.USER_EDITED);
        ReflectionTestUtils.setField(activeRoadmapVersion, "id", activeRoadmapVersionId);
        activeRoadmapVersion.activate(Instant.now());
        roadmap.activateVersion(activeRoadmapVersionId);

        RoadmapItem milestone = RoadmapItem.milestone(
                activeRoadmapVersion,
                "Week 1",
                null,
                0);
        ReflectionTestUtils.setField(milestone, "id", UUID.randomUUID());
        RoadmapItem topic = RoadmapItem.topic(
                activeRoadmapVersion,
                milestone,
                "Spring Security",
                null,
                0,
                120);
        ReflectionTestUtils.setField(topic, "id", UUID.randomUUID());

        DailyPlan targetPlan = DailyPlan.create(
                userId,
                targetDate,
                "Asia/Ho_Chi_Minh",
                roadmapId);
        ReflectionTestUtils.setField(targetPlan, "id", targetPlanId);
        DailyPlanVersion targetVersion = DailyPlanVersion.create(
                targetPlanId,
                1,
                DailyPlanVersionOrigin.MANUAL,
                90,
                0);
        ReflectionTestUtils.setField(targetVersion, "id", targetVersionId);

        DailyPlan priorPlan = DailyPlan.create(
                userId,
                targetDate.minusDays(1),
                "Asia/Ho_Chi_Minh",
                roadmapId);
        ReflectionTestUtils.setField(priorPlan, "id", priorPlanId);
        DailyPlanVersion priorVersion = DailyPlanVersion.create(
                priorPlanId,
                1,
                DailyPlanVersionOrigin.MANUAL,
                60,
                60);
        ReflectionTestUtils.setField(priorVersion, "id", priorVersionId);
        priorVersion.activate(Instant.now());
        priorPlan.activateVersion(priorVersionId);

        DailyPlanItem priorItem = DailyPlanItem.create(
                priorVersionId,
                DailyTaskCategory.PRACTICE,
                "Practice Spring Security",
                null,
                60,
                0,
                topic.getId());
        ReflectionTestUtils.setField(priorItem, "id", priorItemId);
        priorItem.updateStatus(DailyTaskStatus.PARTIALLY_COMPLETED);

        ProgressEntry progress = ProgressEntry.create(
                userId,
                priorItemId,
                ProgressEntryStatus.PARTIALLY_COMPLETED,
                35,
                50,
                "Needs more practice",
                4,
                2,
                null,
                null);
        ReflectionTestUtils.setField(progress, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(progress, "recordedAt", Instant.now());

        when(dailyPlanRepository.findByIdAndUserId(targetPlanId, userId))
                .thenReturn(Optional.of(targetPlan));
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(roadmapVersionRepository.findByIdAndRoadmapId(
                        activeRoadmapVersionId,
                        roadmapId))
                .thenReturn(Optional.of(activeRoadmapVersion));
        when(dailyPlanVersionRepository.findByDailyPlanIdAndStatus(
                        targetPlanId,
                        DailyPlanVersionStatus.DRAFT))
                .thenReturn(Optional.of(targetVersion));
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(
                        activeRoadmapVersionId))
                .thenReturn(List.of(milestone, topic));
        when(dailyPlanRepository
                        .findByUserIdAndRoadmapIdAndPlanDateBeforeOrderByPlanDateDesc(
                                userId,
                                roadmapId,
                                targetDate))
                .thenReturn(List.of(priorPlan));
        when(dailyPlanVersionRepository.findByDailyPlanIdIn(List.of(priorPlanId)))
                .thenReturn(List.of(priorVersion));
        when(dailyPlanItemRepository.findByDailyPlanVersionIds(List.of(priorVersionId)))
                .thenReturn(List.of(priorItem));
        when(progressEntryRepository
                        .findByUserIdAndDailyPlanItemIdInOrderByRecordedAtDesc(
                                userId,
                                List.of(priorItemId)))
                .thenReturn(List.of(progress));
        when(dailyPlanRepository
                        .findFirstByUserIdAndRoadmapIdAndPlanDateBeforeOrderByPlanDateDesc(
                                userId,
                                roadmapId,
                                targetDate))
                .thenReturn(Optional.of(priorPlan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(priorVersionId, priorPlanId))
                .thenReturn(Optional.of(priorVersion));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(priorVersionId))
                .thenReturn(List.of(priorItem));

        DailyPlanningContext context = builder.buildContext(targetPlanId, userId);

        assertThat(context.availableMinutes()).isEqualTo(90);
        assertThat(context.roadmap().activeVersionId()).isEqualTo(activeRoadmapVersionId);
        assertThat(context.roadmap().topics()).extracting(DailyPlanningContext.RoadmapTopic::roadmapItemId)
                .containsExactly(topic.getId());
        assertThat(context.recentProgress()).hasSize(1);
        assertThat(context.latestTopicOutcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.roadmapItemId()).isEqualTo(topic.getId());
            assertThat(outcome.planDate()).isEqualTo(targetDate.minusDays(1));
        });
        assertThat(context.recentProgress().get(0).actualMinutes()).isEqualTo(35);
        assertThat(context.unfinishedTasks()).hasSize(1);
        assertThat(context.weaknessSignals()).hasSize(1);
        assertThat(context.weaknessSignals().get(0).reason())
                .contains("difficulty", "understanding", "partially completed");
    }

    @Test
    void buildContext_neverFallsBackToUnscopedDailyPlanLookup() {
        UUID dailyPlanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> builder.buildContext(dailyPlanId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(dailyPlanRepository).findByIdAndUserId(dailyPlanId, userId);
    }

    @Test
    void deriveWeaknessSignals_usesLatestOutcomeInsteadOfOlderWeakAttempt() {
        UUID roadmapItemId = UUID.randomUUID();
        DailyPlanningContext.ProgressSignal olderWeakAttempt = progressSignal(
                roadmapItemId,
                DailyTaskStatus.PARTIALLY_COMPLETED,
                4,
                2,
                Instant.parse("2026-08-26T08:00:00Z"));
        DailyPlanningContext.ProgressSignal laterSuccessfulAttempt = progressSignal(
                roadmapItemId,
                DailyTaskStatus.COMPLETED,
                2,
                5,
                Instant.parse("2026-08-27T08:00:00Z"));

        List<DailyPlanningContext.WeaknessSignal> result = builder.deriveWeaknessSignals(
                List.of(olderWeakAttempt, laterSuccessfulAttempt));

        assertThat(result).isEmpty();
    }

    private DailyPlanningContext.ProgressSignal progressSignal(
            UUID roadmapItemId,
            DailyTaskStatus status,
            Integer difficulty,
            Integer understandingRating,
            Instant recordedAt) {
        return new DailyPlanningContext.ProgressSignal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                roadmapItemId,
                "Roadmap topic",
                status,
                30,
                30,
                status.completionPercentage(),
                difficulty,
                understandingRating,
                null,
                recordedAt);
    }
}

package com.codegym.aiplanning.service.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.StudyUnitResponse;
import com.codegym.aiplanning.entity.roadmap.ProgressSnapshotStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapStudyUnit;
import com.codegym.aiplanning.entity.roadmap.StudyUnitProgressSnapshot;
import com.codegym.aiplanning.repository.roadmap.RoadmapStudyUnitRepository;
import com.codegym.aiplanning.repository.roadmap.StudyUnitProgressSnapshotRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapItemProgressService.ItemProgressCalculationResult;
import com.codegym.aiplanning.service.roadmap.impl.RoadmapItemProgressServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoadmapItemProgressServiceTest {

    @Mock
    private RoadmapStudyUnitRepository roadmapStudyUnitRepository;

    @Mock
    private StudyUnitProgressSnapshotRepository studyUnitProgressSnapshotRepository;

    private RoadmapItemProgressService progressService;

    private final UUID userId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        progressService = new RoadmapItemProgressServiceImpl(
                roadmapStudyUnitRepository, studyUnitProgressSnapshotRepository);
    }

    private RoadmapStudyUnit createUnit(UUID unitId, String title, int orderIndex) {
        RoadmapStudyUnit unit = RoadmapStudyUnit.create(versionId, itemId, title, null, orderIndex);
        ReflectionTestUtils.setField(unit, "id", unitId);
        return unit;
    }

    private StudyUnitProgressSnapshot createSnapshot(
            UUID unitId, UUID ownerUserId, ProgressSnapshotStatus status) {
        StudyUnitProgressSnapshot snapshot = StudyUnitProgressSnapshot.create(
                ownerUserId, unitId, status, null);
        ReflectionTestUtils.setField(snapshot, "id", UUID.randomUUID());
        return snapshot;
    }

    @Nested
    @DisplayName("1. Zero Units Edge Case")
    class ZeroUnitsTests {

        @Test
        @DisplayName("Should return 0% and isCompleted=false when RoadmapItem has 0 study units")
        void givenZeroUnits_whenCalculateProgress_thenReturnsZeroProgress() {
            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdOrderByOrderIndexAsc(itemId))
                    .thenReturn(List.of());

            ItemProgressCalculationResult result = progressService.calculateProgress(itemId, userId);

            assertThat(result.progress()).isNotNull();
            assertThat(result.progress().completionPercentage()).isEqualTo(0);
            assertThat(result.progress().completedUnitsCount()).isEqualTo(0);
            assertThat(result.progress().totalUnitsCount()).isEqualTo(0);
            assertThat(result.progress().isCompleted()).isFalse();
            assertThat(result.studyUnits()).isEmpty();

            // Verifies read-only behavior: no saving/updating occurred
            verify(studyUnitProgressSnapshotRepository, never()).save(any());
            verify(roadmapStudyUnitRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return empty progress when itemId or userId is null")
        void givenNullInputs_whenCalculateProgress_thenReturnsEmpty() {
            ItemProgressCalculationResult resultWithNullItem =
                    progressService.calculateProgress(null, userId);
            assertThat(resultWithNullItem.progress().isCompleted()).isFalse();
            assertThat(resultWithNullItem.progress().completionPercentage()).isEqualTo(0);

            ItemProgressCalculationResult resultWithNullUser =
                    progressService.calculateProgress(itemId, null);
            assertThat(resultWithNullUser.progress().isCompleted()).isFalse();
            assertThat(resultWithNullUser.progress().completionPercentage()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("2. Percentage Milestones (0%, 25%, 50%, 100%)")
    class PercentageMilestoneTests {

        private UUID u1;
        private UUID u2;
        private UUID u3;
        private UUID u4;
        private List<RoadmapStudyUnit> fourUnits;

        @BeforeEach
        void setUpFourUnits() {
            u1 = UUID.randomUUID();
            u2 = UUID.randomUUID();
            u3 = UUID.randomUUID();
            u4 = UUID.randomUUID();
            fourUnits = List.of(
                    createUnit(u1, "Unit 1", 1),
                    createUnit(u2, "Unit 2", 2),
                    createUnit(u3, "Unit 3", 3),
                    createUnit(u4, "Unit 4", 4));
            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdOrderByOrderIndexAsc(itemId))
                    .thenReturn(fourUnits);
        }

        @Test
        @DisplayName("Case 0%: 4 units, 0 completed -> 0%, isCompleted=false")
        void givenZeroCompleted_whenCalculateProgress_thenZeroPercent() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.NOT_STARTED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(0);
            assertThat(progress.completedUnitsCount()).isEqualTo(0);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Case 25%: 4 units, 1 completed, 3 not started -> 25%, isCompleted=false")
        void givenOneCompleted_whenCalculateProgress_thenTwentyFivePercent() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.NOT_STARTED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(25);
            assertThat(progress.completedUnitsCount()).isEqualTo(1);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Case 50%: 4 units, 2 completed, 2 not started -> 50%, isCompleted=false")
        void givenTwoCompleted_whenCalculateProgress_thenFiftyPercent() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.NOT_STARTED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.NOT_STARTED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(50);
            assertThat(progress.completedUnitsCount()).isEqualTo(2);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Case 100%: 4 units, 4 completed -> 100%, isCompleted=true")
        void givenAllCompleted_whenCalculateProgress_thenHundredPercentAndCompleted() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.COMPLETED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(100);
            assertThat(progress.completedUnitsCount()).isEqualTo(4);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("3. Status Distinction Tests (SKIPPED != COMPLETED, PARTIALLY_COMPLETED != COMPLETED)")
    class StatusDistinctionTests {

        private UUID u1;
        private UUID u2;
        private UUID u3;
        private UUID u4;

        @BeforeEach
        void setUpFourUnits() {
            u1 = UUID.randomUUID();
            u2 = UUID.randomUUID();
            u3 = UUID.randomUUID();
            u4 = UUID.randomUUID();
            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdOrderByOrderIndexAsc(itemId))
                    .thenReturn(List.of(
                            createUnit(u1, "Unit 1", 1),
                            createUnit(u2, "Unit 2", 2),
                            createUnit(u3, "Unit 3", 3),
                            createUnit(u4, "Unit 4", 4)));
        }

        @Test
        @DisplayName("3 COMPLETED + 1 NOT_STARTED -> 75%, isCompleted=false")
        void givenThreeCompletedAndOneNotStarted_whenCalculateProgress_then75PercentNotCompleted() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.NOT_STARTED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(75);
            assertThat(progress.completedUnitsCount()).isEqualTo(3);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("3 COMPLETED + 1 SKIPPED -> 75%, isCompleted=false (SKIPPED != COMPLETED)")
        void givenThreeCompletedAndOneSkipped_whenCalculateProgress_then75PercentNotCompleted() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.SKIPPED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(75);
            assertThat(progress.completedUnitsCount()).isEqualTo(3);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("3 COMPLETED + 1 PARTIALLY_COMPLETED -> 75%, isCompleted=false (PARTIALLY_COMPLETED != COMPLETED)")
        void givenThreeCompletedAndOnePartiallyCompleted_whenCalculateProgress_then75PercentNotCompleted() {
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3, u4))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u3, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u4, userId, ProgressSnapshotStatus.PARTIALLY_COMPLETED)));

            RoadmapItemProgressResponse progress =
                    progressService.calculateItemProgress(itemId, userId);

            assertThat(progress.completionPercentage()).isEqualTo(75);
            assertThat(progress.completedUnitsCount()).isEqualTo(3);
            assertThat(progress.totalUnitsCount()).isEqualTo(4);
            assertThat(progress.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("4. User Isolation Tests")
    class UserIsolationTests {

        @Test
        @DisplayName("User A completing units must NEVER affect User B progress")
        void givenUserACompleted_whenUserBQueries_thenUserBHasZeroCompleted() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            List<RoadmapStudyUnit> units = List.of(
                    createUnit(u1, "Unit 1", 1),
                    createUnit(u2, "Unit 2", 2));

            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdOrderByOrderIndexAsc(itemId))
                    .thenReturn(units);

            // User A has both completed
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userA), eq(List.of(u1, u2))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userA, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userA, ProgressSnapshotStatus.COMPLETED)));

            // User B has no snapshots
            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userB), eq(List.of(u1, u2))))
                    .thenReturn(List.of());

            // Query User A
            RoadmapItemProgressResponse progressA =
                    progressService.calculateItemProgress(itemId, userA);
            assertThat(progressA.completionPercentage()).isEqualTo(100);
            assertThat(progressA.completedUnitsCount()).isEqualTo(2);
            assertThat(progressA.isCompleted()).isTrue();

            // Query User B -> must be isolated, 0 completed!
            RoadmapItemProgressResponse progressB =
                    progressService.calculateItemProgress(itemId, userB);
            assertThat(progressB.completionPercentage()).isEqualTo(0);
            assertThat(progressB.completedUnitsCount()).isEqualTo(0);
            assertThat(progressB.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("5. API Contract & Mapping Tests")
    class ApiContractMappingTests {

        @Test
        @DisplayName("Should return studyUnits array with individual statuses matching snapshot")
        void givenStudyUnitsAndSnapshots_whenCalculateProgress_thenMatchesApiContract() {
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            UUID u3 = UUID.randomUUID();

            RoadmapStudyUnit unit1 = createUnit(u1, "Encapsulation", 1);
            RoadmapStudyUnit unit2 = createUnit(u2, "Inheritance", 2);
            RoadmapStudyUnit unit3 = createUnit(u3, "Polymorphism", 3);

            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdOrderByOrderIndexAsc(itemId))
                    .thenReturn(List.of(unit1, unit2, unit3));

            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1, u2, u3))))
                    .thenReturn(List.of(
                            createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED),
                            createSnapshot(u2, userId, ProgressSnapshotStatus.PARTIALLY_COMPLETED)
                            // u3 has no snapshot -> defaults to NOT_STARTED
                    ));

            ItemProgressCalculationResult result = progressService.calculateProgress(itemId, userId);

            assertThat(result.progress().completionPercentage()).isEqualTo(33);
            assertThat(result.progress().completedUnitsCount()).isEqualTo(1);
            assertThat(result.progress().totalUnitsCount()).isEqualTo(3);
            assertThat(result.progress().isCompleted()).isFalse();

            List<StudyUnitResponse> studyUnits = result.studyUnits();
            assertThat(studyUnits).hasSize(3);

            assertThat(studyUnits.get(0).id()).isEqualTo(u1);
            assertThat(studyUnits.get(0).title()).isEqualTo("Encapsulation");
            assertThat(studyUnits.get(0).orderIndex()).isEqualTo(1);
            assertThat(studyUnits.get(0).status()).isEqualTo(ProgressSnapshotStatus.COMPLETED);

            assertThat(studyUnits.get(1).id()).isEqualTo(u2);
            assertThat(studyUnits.get(1).title()).isEqualTo("Inheritance");
            assertThat(studyUnits.get(1).orderIndex()).isEqualTo(2);
            assertThat(studyUnits.get(1).status()).isEqualTo(ProgressSnapshotStatus.PARTIALLY_COMPLETED);

            assertThat(studyUnits.get(2).id()).isEqualTo(u3);
            assertThat(studyUnits.get(2).title()).isEqualTo("Polymorphism");
            assertThat(studyUnits.get(2).orderIndex()).isEqualTo(3);
            assertThat(studyUnits.get(2).status()).isEqualTo(ProgressSnapshotStatus.NOT_STARTED);
        }

        @Test
        @DisplayName("calculateItemsProgress should return map containing progress for all requested item IDs")
        void givenMultipleItems_whenCalculateItemsProgress_thenReturnsCorrectMap() {
            UUID item1 = UUID.randomUUID();
            UUID item2 = UUID.randomUUID();

            UUID u1 = UUID.randomUUID();
            RoadmapStudyUnit unit1 = RoadmapStudyUnit.create(versionId, item1, "Unit 1", null, 1);
            ReflectionTestUtils.setField(unit1, "id", u1);

            when(roadmapStudyUnitRepository.findAllByRoadmapItemIdInOrderByOrderIndexAsc(List.of(item1, item2)))
                    .thenReturn(List.of(unit1));

            when(studyUnitProgressSnapshotRepository.findAllByUserIdAndRoadmapStudyUnitIdIn(
                    eq(userId), eq(List.of(u1))))
                    .thenReturn(List.of(createSnapshot(u1, userId, ProgressSnapshotStatus.COMPLETED)));

            Map<UUID, ItemProgressCalculationResult> resultMap =
                    progressService.calculateItemsProgress(List.of(item1, item2), userId);

            assertThat(resultMap).containsKeys(item1, item2);

            // Item 1 has 1 unit, completed -> 100%
            assertThat(resultMap.get(item1).progress().completionPercentage()).isEqualTo(100);
            assertThat(resultMap.get(item1).progress().isCompleted()).isTrue();
            assertThat(resultMap.get(item1).studyUnits()).hasSize(1);

            // Item 2 has 0 units -> 0%
            assertThat(resultMap.get(item2).progress().completionPercentage()).isEqualTo(0);
            assertThat(resultMap.get(item2).progress().isCompleted()).isFalse();
            assertThat(resultMap.get(item2).studyUnits()).isEmpty();
        }
    }
}

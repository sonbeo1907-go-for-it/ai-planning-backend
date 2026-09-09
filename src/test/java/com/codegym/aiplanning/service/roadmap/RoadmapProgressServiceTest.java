package com.codegym.aiplanning.service.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapItemProgressService.ItemProgressCalculationResult;
import com.codegym.aiplanning.service.roadmap.impl.RoadmapProgressServiceImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoadmapProgressServiceTest {

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapItemProgressService roadmapItemProgressService;

    private RoadmapProgressService roadmapProgressService;

    private final UUID roadmapId = UUID.randomUUID();
    private final UUID activeVersionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roadmapProgressService = new RoadmapProgressServiceImpl(
                roadmapItemRepository,
                roadmapItemProgressService);
    }

    private RoadmapItem createItem(UUID id, int orderIndex) {
        RoadmapItem item = RoadmapItem.milestone(null, "Item " + orderIndex, null, orderIndex);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private List<RoadmapItem> createItems(int count) {
        List<RoadmapItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(createItem(UUID.randomUUID(), i));
        }
        return items;
    }

    private ItemProgressCalculationResult progressResult(int percentage, boolean completed) {
        return new ItemProgressCalculationResult(
                new RoadmapItemProgressResponse(percentage, completed ? 1 : 0, 1, completed),
                List.of());
    }

    @Test
    @DisplayName("1. Standard counting logic: With 20 items of equal weight, each contributes 5%")
    void standardCountingLogic_20Items() {
        List<RoadmapItem> items = createItems(20);
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items);

        // Case A: 1 completed item -> 5%
        Map<UUID, ItemProgressCalculationResult> map1 = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean completed = (i == 0);
            map1.put(items.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(map1);

        RoadmapProgressResponse result1 = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(result1.totalItemsCount()).isEqualTo(20);
        assertThat(result1.completedItemsCount()).isEqualTo(1);
        assertThat(result1.completionPercentage()).isEqualTo(5);

        // Case B: 10 completed items -> 50%
        Map<UUID, ItemProgressCalculationResult> map10 = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean completed = (i < 10);
            map10.put(items.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(map10);

        RoadmapProgressResponse result10 = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(result10.completedItemsCount()).isEqualTo(10);
        assertThat(result10.completionPercentage()).isEqualTo(50);

        // Case C: 20 completed items -> 100%
        Map<UUID, ItemProgressCalculationResult> map20 = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            map20.put(items.get(i).getId(), progressResult(100, true));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(map20);

        RoadmapProgressResponse result20 = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(result20.completedItemsCount()).isEqualTo(20);
        assertThat(result20.completionPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("2. Incomplete Study Units: Only items with isCompleted = true contribute to completedItemsCount")
    void itemIncompleteStudyUnits_doesNotCountTowardRoadmapCompletion() {
        List<RoadmapItem> items = createItems(4);
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items);

        Map<UUID, ItemProgressCalculationResult> map = new HashMap<>();
        // Item 0: 3/4 units completed (75%), but isCompleted = false
        map.put(items.get(0).getId(), progressResult(75, false));
        // Item 1: 1/2 units completed (50%), but isCompleted = false
        map.put(items.get(1).getId(), progressResult(50, false));
        // Item 2: 0/1 units completed (0%), isCompleted = false
        map.put(items.get(2).getId(), progressResult(0, false));
        // Item 3: 2/2 units completed (100%), isCompleted = true
        map.put(items.get(3).getId(), progressResult(100, true));

        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(map);

        RoadmapProgressResponse result = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);

        assertThat(result.totalItemsCount()).isEqualTo(4);
        assertThat(result.completedItemsCount()).isEqualTo(1);
        assertThat(result.completionPercentage()).isEqualTo(25);
    }

    @Test
    @DisplayName("3. Version Isolation: Old version items do not affect ACTIVE version progress")
    void versionIsolation_oldVersionDoesNotAffectActiveVersion() {
        UUID oldVersionId = UUID.randomUUID();
        List<RoadmapItem> activeItems = createItems(20);
        List<UUID> activeItemIds = activeItems.stream().map(RoadmapItem::getId).toList();

        // Active version has 5/20 completed
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(activeItems);

        Map<UUID, ItemProgressCalculationResult> map = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean completed = (i < 5);
            map.put(activeItems.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(activeItemIds, userId))
                .thenReturn(map);

        RoadmapProgressResponse result = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);

        // Verify repository only queried activeVersionId
        verify(roadmapItemRepository).findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId);
        // Verify oldVersionId was never queried
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIdOrderByOrderIndexAsc(oldVersionId);

        assertThat(result.totalItemsCount()).isEqualTo(20);
        assertThat(result.completedItemsCount()).isEqualTo(5);
        assertThat(result.completionPercentage()).isEqualTo(25);
    }

    @Test
    @DisplayName("4. User Isolation: Scoped to current User and verifies mock delegation receives userId")
    void userIsolation_scopedToCurrentUserAndVerifiesMockDelegation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        List<RoadmapItem> items = createItems(20);
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();

        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items);

        // User A: 10/20 completed
        Map<UUID, ItemProgressCalculationResult> mapA = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean completed = (i < 10);
            mapA.put(items.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userA))
                .thenReturn(mapA);

        RoadmapProgressResponse resultUserA = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userA);

        assertThat(resultUserA.completedItemsCount()).isEqualTo(10);
        assertThat(resultUserA.completionPercentage()).isEqualTo(50);

        // Verification: PROG-05 delegates batch calculation to PROG-04 using userA
        verify(roadmapItemProgressService).calculateItemsProgress(itemIds, userA);
        verify(roadmapItemProgressService, never()).calculateItemsProgress(any(), eq(userB));
    }

    @Test
    @DisplayName("5. Non-reliance on Daily Plan tasks: Progress relies strictly on Roadmap Items")
    void progressDoesNotRelyOnDailyPlan() {
        // Even if 100 daily plan tasks were completed, roadmap has 20 items and only 2 completed
        List<RoadmapItem> items = createItems(20);
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items);

        Map<UUID, ItemProgressCalculationResult> map = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            boolean completed = (i < 2);
            map.put(items.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(map);

        RoadmapProgressResponse result = roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);

        assertThat(result.totalItemsCount()).isEqualTo(20);
        assertThat(result.completedItemsCount()).isEqualTo(2);
        assertThat(result.completionPercentage()).isEqualTo(10);
    }

    @Test
    @DisplayName("6. Edge Cases: No active version, empty items list, or null user return empty response")
    void edgeCases_returnEmptyResponse() {
        // Case A: activeVersionId is null
        RoadmapProgressResponse resNullVersion =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, null, userId);
        assertThat(resNullVersion).isEqualTo(RoadmapProgressResponse.empty());
        verifyNoInteractions(roadmapItemRepository);

        // Case B: userId is null
        RoadmapProgressResponse resNullUser =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, null);
        assertThat(resNullUser).isEqualTo(RoadmapProgressResponse.empty());

        // Case C: active version has 0 items (totalItemsCount = 0)
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(List.of());
        RoadmapProgressResponse resEmptyItems =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(resEmptyItems).isEqualTo(RoadmapProgressResponse.empty());
        assertThat(resEmptyItems.completionPercentage()).isZero();
        assertThat(resEmptyItems.completedItemsCount()).isZero();
        assertThat(resEmptyItems.totalItemsCount()).isZero();
    }

    @Test
    @DisplayName("7. Defensive Edge Cases: calculateItemsProgress returns null or entries with null progress")
    void defensiveNullHandling_whenProgressMapOrEntryIsNull() {
        List<RoadmapItem> items = createItems(2);
        List<UUID> itemIds = items.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items);

        // Subcase A: progressMap is null
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(null);
        RoadmapProgressResponse resNullMap =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(resNullMap.totalItemsCount()).isEqualTo(2);
        assertThat(resNullMap.completedItemsCount()).isZero();
        assertThat(resNullMap.completionPercentage()).isZero();

        // Subcase B: map contains null or entry with null progress
        Map<UUID, ItemProgressCalculationResult> corruptMap = new HashMap<>();
        corruptMap.put(items.get(0).getId(), null);
        corruptMap.put(items.get(1).getId(), new ItemProgressCalculationResult(null, List.of()));
        when(roadmapItemProgressService.calculateItemsProgress(itemIds, userId))
                .thenReturn(corruptMap);
        RoadmapProgressResponse resCorruptMap =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(resCorruptMap.totalItemsCount()).isEqualTo(2);
        assertThat(resCorruptMap.completedItemsCount()).isZero();
        assertThat(resCorruptMap.completionPercentage()).isZero();
    }

    @Test
    @DisplayName("8. Integer arithmetic truncation: Odd number of items calculates correct integer percentage")
    void integerArithmetic_oddNumberOfItems() {
        // 3 items, 1 completed: 1 * 100 / 3 = 33%
        List<RoadmapItem> items3 = createItems(3);
        List<UUID> itemIds3 = items3.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items3);

        Map<UUID, ItemProgressCalculationResult> map3 = new HashMap<>();
        map3.put(items3.get(0).getId(), progressResult(100, true));
        map3.put(items3.get(1).getId(), progressResult(0, false));
        map3.put(items3.get(2).getId(), progressResult(0, false));
        when(roadmapItemProgressService.calculateItemsProgress(itemIds3, userId))
                .thenReturn(map3);

        RoadmapProgressResponse res3 =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(res3.totalItemsCount()).isEqualTo(3);
        assertThat(res3.completedItemsCount()).isEqualTo(1);
        assertThat(res3.completionPercentage()).isEqualTo(33);

        // 7 items, 2 completed: 2 * 100 / 7 = 28%
        List<RoadmapItem> items7 = createItems(7);
        List<UUID> itemIds7 = items7.stream().map(RoadmapItem::getId).toList();
        when(roadmapItemRepository.findAllByRoadmapVersionIdOrderByOrderIndexAsc(activeVersionId))
                .thenReturn(items7);

        Map<UUID, ItemProgressCalculationResult> map7 = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            boolean completed = (i < 2);
            map7.put(items7.get(i).getId(), progressResult(completed ? 100 : 0, completed));
        }
        when(roadmapItemProgressService.calculateItemsProgress(itemIds7, userId))
                .thenReturn(map7);

        RoadmapProgressResponse res7 =
                roadmapProgressService.calculateRoadmapProgress(roadmapId, activeVersionId, userId);
        assertThat(res7.totalItemsCount()).isEqualTo(7);
        assertThat(res7.completedItemsCount()).isEqualTo(2);
        assertThat(res7.completionPercentage()).isEqualTo(28);
    }
}

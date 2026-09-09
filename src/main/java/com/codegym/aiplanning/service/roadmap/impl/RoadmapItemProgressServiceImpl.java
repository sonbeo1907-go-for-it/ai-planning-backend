package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemProgressResponse;
import com.codegym.aiplanning.controller.roadmap.dto.StudyUnitResponse;
import com.codegym.aiplanning.entity.roadmap.ProgressSnapshotStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapStudyUnit;
import com.codegym.aiplanning.entity.roadmap.StudyUnitProgressSnapshot;
import com.codegym.aiplanning.repository.roadmap.RoadmapStudyUnitRepository;
import com.codegym.aiplanning.repository.roadmap.StudyUnitProgressSnapshotRepository;
import com.codegym.aiplanning.service.roadmap.RoadmapItemProgressService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoadmapItemProgressServiceImpl implements RoadmapItemProgressService {

    private final RoadmapStudyUnitRepository roadmapStudyUnitRepository;
    private final StudyUnitProgressSnapshotRepository studyUnitProgressSnapshotRepository;

    public RoadmapItemProgressServiceImpl(
            RoadmapStudyUnitRepository roadmapStudyUnitRepository,
            StudyUnitProgressSnapshotRepository studyUnitProgressSnapshotRepository) {
        this.roadmapStudyUnitRepository = roadmapStudyUnitRepository;
        this.studyUnitProgressSnapshotRepository = studyUnitProgressSnapshotRepository;
    }

    @Override
    public ItemProgressCalculationResult calculateProgress(UUID roadmapItemId, UUID userId) {
        if (roadmapItemId == null || userId == null) {
            return new ItemProgressCalculationResult(RoadmapItemProgressResponse.empty(), List.of());
        }

        List<RoadmapStudyUnit> units = roadmapStudyUnitRepository
                .findAllByRoadmapItemIdOrderByOrderIndexAsc(roadmapItemId);

        if (units.isEmpty()) {
            return new ItemProgressCalculationResult(RoadmapItemProgressResponse.empty(), List.of());
        }

        List<UUID> unitIds = units.stream().map(RoadmapStudyUnit::getId).toList();
        List<StudyUnitProgressSnapshot> snapshots = studyUnitProgressSnapshotRepository
                .findAllByUserIdAndRoadmapStudyUnitIdIn(userId, unitIds);

        Map<UUID, ProgressSnapshotStatus> statusByUnitId = snapshots.stream()
                .collect(Collectors.toMap(
                        StudyUnitProgressSnapshot::getRoadmapStudyUnitId,
                        StudyUnitProgressSnapshot::getStatus,
                        (existing, replacement) -> existing));

        List<StudyUnitResponse> studyUnitResponses = units.stream()
                .map(unit -> StudyUnitResponse.from(
                        unit,
                        statusByUnitId.getOrDefault(unit.getId(), ProgressSnapshotStatus.NOT_STARTED)))
                .toList();

        int totalUnitsCount = studyUnitResponses.size();
        int completedUnitsCount = (int) studyUnitResponses.stream()
                .filter(unit -> unit.status() == ProgressSnapshotStatus.COMPLETED)
                .count();

        int completionPercentage = totalUnitsCount > 0
                ? (completedUnitsCount * 100) / totalUnitsCount
                : 0;

        boolean isCompleted = totalUnitsCount > 0 && completedUnitsCount == totalUnitsCount;

        RoadmapItemProgressResponse progress = new RoadmapItemProgressResponse(
                completionPercentage,
                completedUnitsCount,
                totalUnitsCount,
                isCompleted);

        return new ItemProgressCalculationResult(progress, studyUnitResponses);
    }

    @Override
    public RoadmapItemProgressResponse calculateItemProgress(UUID roadmapItemId, UUID userId) {
        return calculateProgress(roadmapItemId, userId).progress();
    }

    @Override
    public List<StudyUnitResponse> getStudyUnitsWithProgress(UUID roadmapItemId, UUID userId) {
        return calculateProgress(roadmapItemId, userId).studyUnits();
    }

    @Override
    public Map<UUID, ItemProgressCalculationResult> calculateItemsProgress(
            Collection<UUID> roadmapItemIds, UUID userId) {
        if (roadmapItemIds == null || roadmapItemIds.isEmpty() || userId == null) {
            return Map.of();
        }

        List<RoadmapStudyUnit> allUnits = roadmapStudyUnitRepository
                .findAllByRoadmapItemIdInOrderByOrderIndexAsc(roadmapItemIds);

        if (allUnits.isEmpty()) {
            Map<UUID, ItemProgressCalculationResult> emptyResults = new HashMap<>();
            for (UUID itemId : roadmapItemIds) {
                emptyResults.put(itemId, new ItemProgressCalculationResult(
                        RoadmapItemProgressResponse.empty(), List.of()));
            }
            return emptyResults;
        }

        Map<UUID, List<RoadmapStudyUnit>> unitsByItemId = new HashMap<>();
        List<UUID> allUnitIds = new ArrayList<>();
        for (RoadmapStudyUnit unit : allUnits) {
            unitsByItemId
                    .computeIfAbsent(unit.getRoadmapItemId(), ignored -> new ArrayList<>())
                    .add(unit);
            allUnitIds.add(unit.getId());
        }

        List<StudyUnitProgressSnapshot> snapshots = studyUnitProgressSnapshotRepository
                .findAllByUserIdAndRoadmapStudyUnitIdIn(userId, allUnitIds);

        Map<UUID, ProgressSnapshotStatus> statusByUnitId = snapshots.stream()
                .collect(Collectors.toMap(
                        StudyUnitProgressSnapshot::getRoadmapStudyUnitId,
                        StudyUnitProgressSnapshot::getStatus,
                        (existing, replacement) -> existing));

        Map<UUID, ItemProgressCalculationResult> resultMap = new HashMap<>();
        for (UUID itemId : roadmapItemIds) {
            List<RoadmapStudyUnit> itemUnits = unitsByItemId.getOrDefault(itemId, Collections.emptyList());
            if (itemUnits.isEmpty()) {
                resultMap.put(itemId, new ItemProgressCalculationResult(
                        RoadmapItemProgressResponse.empty(), List.of()));
                continue;
            }

            List<StudyUnitResponse> studyUnitResponses = itemUnits.stream()
                    .map(unit -> StudyUnitResponse.from(
                            unit,
                            statusByUnitId.getOrDefault(unit.getId(), ProgressSnapshotStatus.NOT_STARTED)))
                    .toList();

            int totalUnitsCount = studyUnitResponses.size();
            int completedUnitsCount = (int) studyUnitResponses.stream()
                    .filter(unit -> unit.status() == ProgressSnapshotStatus.COMPLETED)
                    .count();

            int completionPercentage = totalUnitsCount > 0
                    ? (completedUnitsCount * 100) / totalUnitsCount
                    : 0;

            boolean isCompleted = totalUnitsCount > 0 && completedUnitsCount == totalUnitsCount;

            RoadmapItemProgressResponse progress = new RoadmapItemProgressResponse(
                    completionPercentage,
                    completedUnitsCount,
                    totalUnitsCount,
                    isCompleted);

            resultMap.put(itemId, new ItemProgressCalculationResult(progress, studyUnitResponses));
        }

        return resultMap;
    }
}

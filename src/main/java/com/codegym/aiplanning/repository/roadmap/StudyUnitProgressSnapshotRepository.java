package com.codegym.aiplanning.repository.roadmap;

import com.codegym.aiplanning.entity.roadmap.StudyUnitProgressSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyUnitProgressSnapshotRepository
        extends JpaRepository<StudyUnitProgressSnapshot, UUID> {

    Optional<StudyUnitProgressSnapshot> findByUserIdAndRoadmapStudyUnitId(
            UUID userId, UUID roadmapStudyUnitId);

    List<StudyUnitProgressSnapshot> findAllByUserIdAndRoadmapStudyUnitIdIn(
            UUID userId, Collection<UUID> roadmapStudyUnitIds);
}

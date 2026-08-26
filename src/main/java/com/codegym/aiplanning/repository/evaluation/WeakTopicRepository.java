package com.codegym.aiplanning.repository.evaluation;

import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeakTopicRepository extends JpaRepository<WeakTopic, UUID> {

    Optional<WeakTopic> findByIdAndUserId(UUID id, UUID userId);

    Optional<WeakTopic> findByUserIdAndRoadmapItemId(UUID userId, UUID roadmapItemId);

    List<WeakTopic> findByUserIdAndRoadmapIdOrderByCreatedAtDesc(UUID userId, UUID roadmapId);

    List<WeakTopic> findByUserIdAndRoadmapIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, UUID roadmapId, Collection<WeakTopicStatus> statuses);

    @Query("""
            SELECT wt FROM WeakTopic wt
            JOIN FETCH wt.roadmapItem ri
            WHERE wt.user.id = :userId
              AND wt.roadmap.id = :roadmapId
              AND wt.status IN :statuses
            ORDER BY wt.unresolvedAt DESC
            """)
    List<WeakTopic> findWithItemByUserIdAndRoadmapIdAndStatusIn(
            @Param("userId") UUID userId,
            @Param("roadmapId") UUID roadmapId,
            @Param("statuses") Collection<WeakTopicStatus> statuses);
}

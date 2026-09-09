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

    @Query("""
            SELECT wt FROM WeakTopic wt
            JOIN FETCH wt.user user
            JOIN FETCH wt.roadmap roadmap
            JOIN FETCH wt.roadmapVersion roadmapVersion
            JOIN FETCH wt.roadmapItem item
            LEFT JOIN FETCH item.parent parent
            LEFT JOIN FETCH parent.parent milestone
            WHERE wt.id = :id AND user.id = :userId
            """)
    Optional<WeakTopic> findWithContextByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    Optional<WeakTopic> findByUserIdAndRoadmapItemId(UUID userId, UUID roadmapItemId);

    List<WeakTopic> findByUserIdAndRoadmapIdOrderByCreatedAtDesc(UUID userId, UUID roadmapId);

    @Query("""
            SELECT DISTINCT wt FROM WeakTopic wt
            JOIN FETCH wt.roadmapItem ri
            LEFT JOIN FETCH ri.parent parent
            LEFT JOIN FETCH parent.parent milestone
            WHERE wt.user.id = :userId
              AND wt.roadmap.id = :roadmapId
              AND wt.status IN :statuses
            ORDER BY wt.unresolvedAt DESC
            """)
    List<WeakTopic> findWithItemByUserIdAndRoadmapIdAndStatusIn(
            @Param("userId") UUID userId,
            @Param("roadmapId") UUID roadmapId,
            @Param("statuses") Collection<WeakTopicStatus> statuses);

    @Query("""
            SELECT DISTINCT wt FROM WeakTopic wt
            JOIN FETCH wt.roadmapItem item
            LEFT JOIN FETCH item.parent parent
            LEFT JOIN FETCH parent.parent milestone
            WHERE wt.user.id = :userId
              AND wt.roadmapVersion.id = :roadmapVersionId
              AND wt.status IN :statuses
            ORDER BY wt.unresolvedAt DESC
            """)
    List<WeakTopic> findWithItemByUserIdAndRoadmapVersionIdAndStatusIn(
            @Param("userId") UUID userId,
            @Param("roadmapVersionId") UUID roadmapVersionId,
            @Param("statuses") Collection<WeakTopicStatus> statuses);
}

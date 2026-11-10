package com.codegym.aiplanning.repository.guidance;

import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevision;
import com.codegym.aiplanning.entity.guidance.TaskGuidanceRevisionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGuidanceRevisionRepository
        extends JpaRepository<TaskGuidanceRevision, UUID> {

    Optional<TaskGuidanceRevision> findByAiExecutionId(UUID aiExecutionId);

    Optional<TaskGuidanceRevision> findByIdAndTaskGuidanceId(
            UUID revisionId, UUID taskGuidanceId);

    Optional<TaskGuidanceRevision>
            findFirstByTaskGuidanceIdAndStatusOrderByRevisionNumberDesc(
                    UUID taskGuidanceId,
                    TaskGuidanceRevisionStatus status);

    Optional<TaskGuidanceRevision>
            findFirstByTaskGuidanceIdOrderByRevisionNumberDesc(UUID taskGuidanceId);

    Page<TaskGuidanceRevision> findByTaskGuidanceIdOrderByRevisionNumberDesc(
            UUID taskGuidanceId,
            Pageable pageable);

    Page<TaskGuidanceRevision> findByTaskGuidanceIdAndStatusInOrderByRevisionNumberDesc(
            UUID taskGuidanceId,
            List<TaskGuidanceRevisionStatus> statuses,
            Pageable pageable);

    List<TaskGuidanceRevision> findByTaskGuidanceIdAndStatusInOrderByRevisionNumberDesc(
            UUID taskGuidanceId,
            List<TaskGuidanceRevisionStatus> statuses);
}

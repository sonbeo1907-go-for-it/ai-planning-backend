package com.codegym.aiplanning.repository.guidance;

import com.codegym.aiplanning.entity.guidance.TaskStepGuidance;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStepGuidanceRepository
        extends JpaRepository<TaskStepGuidance, UUID> {

    List<TaskStepGuidance> findByRevisionIdOrderByOrderIndex(UUID revisionId);

    List<TaskStepGuidance> findByRevisionIdInOrderByRevisionIdAscOrderIndexAsc(
            Collection<UUID> revisionIds);
}

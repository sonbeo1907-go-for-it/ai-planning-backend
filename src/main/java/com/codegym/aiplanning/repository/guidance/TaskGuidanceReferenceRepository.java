package com.codegym.aiplanning.repository.guidance;

import com.codegym.aiplanning.entity.guidance.TaskGuidanceReference;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGuidanceReferenceRepository
        extends JpaRepository<TaskGuidanceReference, UUID> {

    List<TaskGuidanceReference> findByRevisionIdOrderByOrderIndex(UUID revisionId);

    List<TaskGuidanceReference> findByRevisionIdInOrderByRevisionIdAscOrderIndexAsc(
            Collection<UUID> revisionIds);
}

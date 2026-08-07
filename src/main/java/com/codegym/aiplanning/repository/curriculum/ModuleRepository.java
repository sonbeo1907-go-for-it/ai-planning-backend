package com.codegym.aiplanning.repository.curriculum;

import com.codegym.aiplanning.entity.curriculum.Module;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleRepository extends JpaRepository<Module, UUID> {

    boolean existsByCourseIdAndCode(UUID courseId, String code);

    boolean existsByCourseIdAndSequenceNumber(UUID courseId, Integer sequenceNumber);

    @Query("SELECT MAX(m.sequenceNumber) FROM Module m WHERE m.courseId = :courseId")
    Optional<Integer> findMaxSequenceNumberByCourseId(@Param("courseId") UUID courseId);

    List<Module> findByCourseIdOrderBySequenceNumberAsc(UUID courseId);
}

package com.codegym.aiplanning.repository.course;

import com.codegym.aiplanning.entity.course.StudyClass;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyClassRepository extends JpaRepository<StudyClass, UUID> {
    Optional<StudyClass> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCourseId(UUID courseId);
}

package com.codegym.aiplanning.repository.course;

import com.codegym.aiplanning.entity.course.StudyClass;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyClassRepository extends JpaRepository<StudyClass, UUID>, JpaSpecificationExecutor<StudyClass> {
    boolean existsByCode(String code);
}

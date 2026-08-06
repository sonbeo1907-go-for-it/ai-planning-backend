package com.codegym.aiplanning.repository.course;

import com.codegym.aiplanning.entity.course.Course;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseRepository
        extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    boolean existsByCodeIgnoreCase(String code);
}

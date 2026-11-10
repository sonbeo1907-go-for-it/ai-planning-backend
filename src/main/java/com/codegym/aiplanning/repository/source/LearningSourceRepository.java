package com.codegym.aiplanning.repository.source;

import com.codegym.aiplanning.entity.source.LearningSource;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningSourceRepository extends JpaRepository<LearningSource, UUID> {}

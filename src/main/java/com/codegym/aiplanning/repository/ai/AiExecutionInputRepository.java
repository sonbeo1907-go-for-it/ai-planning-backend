package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.entity.ai.AiExecutionInput;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiExecutionInputRepository
        extends JpaRepository<AiExecutionInput, UUID> {

    void deleteByExecutionId(UUID executionId);
}

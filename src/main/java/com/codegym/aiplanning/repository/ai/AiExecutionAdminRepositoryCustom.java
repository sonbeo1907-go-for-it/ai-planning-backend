package com.codegym.aiplanning.repository.ai;

import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.entity.ai.AiExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiExecutionAdminRepositoryCustom {

    Page<AiExecution> searchAdminExecutions(AdminAiExecutionFilter filter, Pageable pageable);
}

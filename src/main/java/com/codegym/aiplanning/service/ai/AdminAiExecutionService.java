package com.codegym.aiplanning.service.ai;

import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionDetailResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionListResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAiExecutionService {

    Page<AdminAiExecutionListResponse> listExecutions(AdminAiExecutionFilter filter, Pageable pageable);

    AdminAiExecutionDetailResponse getExecutionDetail(UUID executionId);
}

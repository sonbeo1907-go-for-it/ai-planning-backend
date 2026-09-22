package com.codegym.aiplanning.controller.admin.ai;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionDetailResponse;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionListResponse;
import com.codegym.aiplanning.service.ai.AdminAiExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ADMIN_AI_EXECUTIONS)
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Admin AI Executions",
        description = "ADMIN-only AI execution inspection, operational filtering, and sanitized diagnostics")
public class AdminAiExecutionController {

    private final AdminAiExecutionService adminAiExecutionService;

    public AdminAiExecutionController(AdminAiExecutionService adminAiExecutionService) {
        this.adminAiExecutionService = adminAiExecutionService;
    }

    @GetMapping
    @Operation(summary = "Get paginated AI execution history with operational filters")
    public ApiResponse<Page<AdminAiExecutionListResponse>> listExecutions(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "providerId", required = false) UUID providerId,
            @RequestParam(name = "model", required = false) String model,
            @RequestParam(name = "purpose", required = false) String purpose,
            @RequestParam(name = "operation", required = false) String operation,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "failureCode", required = false) String failureCode,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                from, to, providerId, model, purpose, operation, status, failureCode);
        return ApiResponse.of(adminAiExecutionService.listExecutions(filter, pageable));
    }

    @GetMapping(ApiConstant.AI_EXECUTION_BY_ID)
    @Operation(summary = "Get sanitized AI execution diagnostic details and timeline")
    public ApiResponse<AdminAiExecutionDetailResponse> getExecutionDetail(
            @PathVariable("executionId") UUID executionId) {
        return ApiResponse.of(adminAiExecutionService.getExecutionDetail(executionId));
    }
}

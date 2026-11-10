package com.codegym.aiplanning.controller.guidance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.ai.dto.AiExecutionResponse;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.execution.AiExecutionService;
import com.codegym.aiplanning.service.guidance.TaskGuidanceQueryService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class TaskGuidanceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiExecutionService aiExecutionService;

    @MockitoBean
    private TaskGuidanceQueryService queryService;

    @Test
    void userCanQueueOneOwnerScopedGuidanceExecution() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(aiExecutionService.submitTaskGuidanceGeneration(
                        ownerId,
                        planId,
                        versionId,
                        itemId,
                        "guidance-key"))
                .thenReturn(execution(executionId, itemId));

        mockMvc.perform(post(generatePath(planId, versionId, itemId))
                        .with(jwt()
                                .jwt(builder -> builder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", "guidance-key"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(executionId.toString()))
                .andExpect(jsonPath("$.data.purpose")
                        .value("TASK_GUIDANCE_GENERATION"))
                .andExpect(jsonPath("$.data.targetType")
                        .value("DAILY_PLAN_ITEM"));
    }

    @Test
    void adminCannotGeneratePersonalTaskGuidance() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(post(generatePath(planId, versionId, itemId))
                        .with(jwt()
                                .jwt(builder -> builder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(aiExecutionService, never()).submitTaskGuidanceGeneration(
                any(), any(), any(), any(), any());
    }

    @Test
    void regenerationAcceptsOnlyBoundedAdjustmentText() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(aiExecutionService.submitTaskGuidanceRegeneration(
                        eq(ownerId),
                        eq(planId),
                        eq(versionId),
                        eq(itemId),
                        eq("Use a smaller example"),
                        eq(null)))
                .thenReturn(execution(executionId, itemId));

        mockMvc.perform(post(regeneratePath(planId, versionId, itemId))
                        .with(jwt()
                                .jwt(builder -> builder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustmentInstruction\":\"  Use a smaller example  \"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(regeneratePath(planId, versionId, itemId))
                        .with(jwt()
                                .jwt(builder -> builder.subject(ownerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustmentInstruction\":\""
                                + "x".repeat(1001)
                                + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private String generatePath(UUID planId, UUID versionId, UUID itemId) {
        return ApiConstant.DAILY_PLANS
                + "/" + planId
                + "/versions/" + versionId
                + "/items/" + itemId
                + "/guidance/generate";
    }

    private String regeneratePath(UUID planId, UUID versionId, UUID itemId) {
        return ApiConstant.DAILY_PLANS
                + "/" + planId
                + "/versions/" + versionId
                + "/items/" + itemId
                + "/guidance/regenerate";
    }

    private AiExecutionResponse execution(UUID executionId, UUID itemId) {
        Instant now = Instant.now();
        return new AiExecutionResponse(
                executionId,
                0L,
                UUID.randomUUID(),
                AiPurpose.TASK_GUIDANCE_GENERATION,
                AiExecutionOperation.GENERATE,
                AiExecutionTargetType.DAILY_PLAN_ITEM,
                itemId,
                AiExecutionStatus.QUEUED,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                now,
                now);
    }
}

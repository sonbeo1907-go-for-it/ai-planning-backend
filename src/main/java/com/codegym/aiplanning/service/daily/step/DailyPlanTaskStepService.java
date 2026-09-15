package com.codegym.aiplanning.service.daily.step;

import com.codegym.aiplanning.controller.daily.dto.CreateTaskStepRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanTaskStepsResponse;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepCompletionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStepRequest;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface DailyPlanTaskStepService {

    DailyPlanTaskStepsResponse getSteps(
            UUID planId,
            UUID versionId,
            UUID itemId,
            Jwt actorJwt);

    DailyPlanTaskStepsResponse createStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            CreateTaskStepRequest request,
            Jwt actorJwt);

    DailyPlanTaskStepsResponse updateStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            UpdateTaskStepRequest request,
            Jwt actorJwt);

    DailyPlanTaskStepsResponse deleteStep(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            long expectedEntityVersion,
            Jwt actorJwt);

    DailyPlanTaskStepsResponse setCompletion(
            UUID planId,
            UUID versionId,
            UUID itemId,
            UUID stepId,
            UpdateTaskStepCompletionRequest request,
            Jwt actorJwt);
}

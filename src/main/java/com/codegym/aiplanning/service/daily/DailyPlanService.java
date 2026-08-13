package com.codegym.aiplanning.service.daily;

import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.UpdateTaskStatusRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface DailyPlanService {

    DailyPlanResponse createDailyPlan(CreateDailyPlanRequest request, Jwt actorJwt);

    DailyPlanResponse getTodayPlan(Jwt actorJwt);

    DailyPlanResponse getPlanById(UUID planId, Jwt actorJwt);

    List<DailyPlanResponse> getUserDailyPlans(Jwt actorJwt);

    DailyPlanItemResponse addTaskToPlan(UUID planId, CreateDailyTaskRequest request, Jwt actorJwt);

    DailyPlanResponse activateVersion(UUID planId, UUID versionId, Jwt actorJwt);

    DailyPlanItemResponse recordProgress(UUID planId, UUID itemId, com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest request, Jwt actorJwt);

    DailyPlanItemResponse recordPomodoroSession(UUID planId, UUID itemId, RecordPomodoroSessionRequest request, Jwt actorJwt);

    DailyPlanResponse deleteTask(UUID planId, UUID itemId, Jwt actorJwt);
}

package com.codegym.aiplanning.service.curriculum;

import com.codegym.aiplanning.controller.curriculum.dto.CreateModuleRequest;
import com.codegym.aiplanning.controller.curriculum.dto.ModuleResponse;
import com.codegym.aiplanning.controller.curriculum.dto.ReorderModulesRequest;
import com.codegym.aiplanning.controller.curriculum.dto.UpdateModuleRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface ModuleService {

    ModuleResponse createModule(UUID courseId, CreateModuleRequest request, Jwt actorJwt);

    ModuleResponse updateModule(UUID courseId, UUID moduleId, UpdateModuleRequest request, Jwt actorJwt);

    List<ModuleResponse> reorderModules(UUID courseId, ReorderModulesRequest request, Jwt actorJwt);

    ModuleResponse activateModule(UUID courseId, UUID moduleId, Jwt actorJwt);

    ModuleResponse deactivateModule(UUID courseId, UUID moduleId, Jwt actorJwt);

    List<ModuleResponse> getModulesByCourseId(UUID courseId);
}

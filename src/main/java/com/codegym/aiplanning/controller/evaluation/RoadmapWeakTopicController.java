package com.codegym.aiplanning.controller.evaluation;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.ROADMAPS)
@PreAuthorize("hasRole('USER')")
@Tag(name = "Weak Topics", description = "Owner-scoped adaptive learning topics")
public class RoadmapWeakTopicController {

    private final WeakTopicService weakTopicService;

    public RoadmapWeakTopicController(WeakTopicService weakTopicService) {
        this.weakTopicService = weakTopicService;
    }

    @GetMapping(ApiConstant.ROADMAP_WEAK_TOPICS)
    @Operation(summary = "List Weak Topics for an owned Roadmap")
    public ApiResponse<List<WeakTopicResponse>> list(
            @PathVariable UUID roadmapId,
            @RequestParam(required = false) Set<WeakTopicStatus> status,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(weakTopicService.getWeakTopics(
                UUID.fromString(jwt.getSubject()),
                roadmapId,
                status));
    }
}

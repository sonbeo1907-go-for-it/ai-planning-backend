package com.codegym.aiplanning.controller.evaluation;

import com.codegym.aiplanning.common.api.ApiResponse;
import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstant.API_V1)
@PreAuthorize("hasRole('USER')")
@Tag(name = "Weak Topics", description = "Weak topic review and mastery check APIs (US-WEK-03)")
public class WeakTopicController {

    private final WeakTopicService weakTopicService;

    public WeakTopicController(WeakTopicService weakTopicService) {
        this.weakTopicService = weakTopicService;
    }

    @GetMapping(ApiConstant.ROADMAPS + ApiConstant.ROADMAP_WEAK_TOPICS)
    @Operation(summary = "List weak topics for an owned roadmap")
    public ApiResponse<List<WeakTopicResponse>> getWeakTopics(
            @PathVariable UUID roadmapId,
            @RequestParam(required = false) Set<WeakTopicStatus> status,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(weakTopicService.getWeakTopics(
                UUID.fromString(jwt.getSubject()), roadmapId, status));
    }

    @PostMapping(ApiConstant.WEAK_TOPICS + ApiConstant.WEAK_TOPIC_MASTERY_CHECK_GENERATE)
    @Operation(summary = "Generate a reinforcement quiz for a weak topic")
    public ApiResponse<QuizDetailResponse> generateMasteryCheck(
            @PathVariable UUID weakTopicId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(weakTopicService.generateMasteryCheckQuiz(
                UUID.fromString(jwt.getSubject()), weakTopicId));
    }

    @PostMapping(ApiConstant.WEAK_TOPICS + ApiConstant.WEAK_TOPIC_MASTERY_CHECK_SUBMIT)
    @Operation(summary = "Submit a weak topic mastery check")
    public ApiResponse<MasteryCheckResultResponse> submitMasteryCheck(
            @PathVariable UUID weakTopicId,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.of(weakTopicService.submitMasteryCheck(
                UUID.fromString(jwt.getSubject()), weakTopicId, quizId, request));
    }
}

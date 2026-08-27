package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SelfEvaluationRequest;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.entity.evaluation.DailyEvaluation;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.evaluation.DailyEvaluationRepository;
import com.codegym.aiplanning.service.evaluation.DailyEvaluationService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.codegym.aiplanning.service.evaluation.impl.DailyEvaluationPersistenceService.DailyQuizContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyEvaluationServiceImpl implements DailyEvaluationService {

    private final DailyEvaluationPersistenceService persistenceService;
    private final QuizGeneratorService quizGeneratorService;
    private final DailyEvaluationRepository dailyEvaluationRepository;
    private final UserAccountRepository userAccountRepository;
    private final DailyPlanRepository dailyPlanRepository;

    public DailyEvaluationServiceImpl(
            DailyEvaluationPersistenceService persistenceService,
            QuizGeneratorService quizGeneratorService,
            DailyEvaluationRepository dailyEvaluationRepository,
            UserAccountRepository userAccountRepository,
            DailyPlanRepository dailyPlanRepository) {
        this.persistenceService = persistenceService;
        this.quizGeneratorService = quizGeneratorService;
        this.dailyEvaluationRepository = dailyEvaluationRepository;
        this.userAccountRepository = userAccountRepository;
        this.dailyPlanRepository = dailyPlanRepository;
    }

    @Override
    public QuizDetailResponse generateDailyQuiz(UUID userId, UUID dailyPlanId) {
        return generateDailyQuiz(userId, dailyPlanId, false);
    }

    @Override
    public QuizDetailResponse generateDailyQuiz(UUID userId, UUID dailyPlanId, boolean forceNew) {
        // Check if there is an unsubmitted GENERATED quiz
        Optional<QuizDetailResponse> unsubmitted = persistenceService.findExistingGeneratedQuiz(userId, dailyPlanId);
        if (unsubmitted.isPresent()) {
            return unsubmitted.get();
        }

        // If not forceNew, and an existing quiz exists, return latest
        if (!forceNew) {
            Optional<QuizDetailResponse> latest = persistenceService.findLatestDailyQuiz(userId, dailyPlanId);
            if (latest.isPresent()) {
                return latest.get();
            }
        }

        // Point 2: 3-Phase Execution (Phase 1: Read Tx)
        DailyQuizContext context = persistenceService.prepareDailyQuizContext(userId, dailyPlanId);

        // Point 2: Phase 2 (Non-Tx AI Generation & Schema Validation with retries)
        GeneratedQuizPlan generatedPlan = quizGeneratorService.generateDailyQuizQuestions(
                userId, dailyPlanId, context.completedTopicItemIds());

        // Point 2: Phase 3 (Write Tx Save Quiz)
        return persistenceService.saveGeneratedDailyQuiz(
                userId, dailyPlanId, context.roadmap().getId(), generatedPlan);
    }

    @Override
    public QuizDetailResponse submitDailyQuiz(
            UUID userId, UUID dailyPlanId, UUID quizId, SubmitQuizRequest request) {
        return persistenceService.submitDailyQuiz(userId, dailyPlanId, quizId, request);
    }

    @Override
    public Optional<QuizDetailResponse> getLatestDailyQuiz(UUID userId, UUID dailyPlanId) {
        return persistenceService.findLatestDailyQuiz(userId, dailyPlanId);
    }

    @Override
    public List<QuizDetailResponse> getAllDailyQuizzes(UUID userId, UUID dailyPlanId) {
        return persistenceService.getAllDailyQuizzes(userId, dailyPlanId);
    }

    @Override
    public QuizDetailResponse getDailyQuizById(UUID userId, UUID dailyPlanId, UUID quizId) {
        return persistenceService.getDailyQuizById(userId, dailyPlanId, quizId);
    }

    @Override
    @Transactional
    public DailyEvaluationResponse recordSelfEvaluation(
            UUID userId, UUID dailyPlanId, SelfEvaluationRequest request) {
        var dailyPlan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId)
                .orElseThrow(() -> new com.codegym.aiplanning.common.exception.BusinessException(
                        com.codegym.aiplanning.common.exception.ErrorCode.RESOURCE_NOT_FOUND,
                        "Daily plan not found."));

        var user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new com.codegym.aiplanning.common.exception.BusinessException(
                        com.codegym.aiplanning.common.exception.ErrorCode.RESOURCE_NOT_FOUND,
                        "User not found."));

        DailyEvaluation evaluation = dailyEvaluationRepository.findByUserIdAndDailyPlanId(userId, dailyPlanId)
                .orElseGet(() -> DailyEvaluation.create(
                        user,
                        dailyPlan,
                        dailyPlan.getPlanDate(),
                        null,
                        null,
                        null,
                        null));

        evaluation.updateSelfRating(request.overallRating(), request.feedbackNote());
        DailyEvaluation saved = dailyEvaluationRepository.save(evaluation);

        return new DailyEvaluationResponse(
                saved.getId(),
                saved.getDailyPlan().getId(),
                saved.getEvaluationDate(),
                saved.getQuizScore(),
                saved.getQuizPassed(),
                saved.getOverallRating(),
                saved.getFeedbackNote(),
                saved.getCreatedAt());
    }

    @Override
    public DailyEvaluationResponse getDailyEvaluation(UUID userId, UUID dailyPlanId) {
        return persistenceService.getDailyEvaluation(userId, dailyPlanId);
    }
}

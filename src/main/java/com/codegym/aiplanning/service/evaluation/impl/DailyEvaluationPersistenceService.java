package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto;
import com.codegym.aiplanning.controller.evaluation.dto.QuizQuestionResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.AnswerSubmissionDto;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.evaluation.DailyEvaluation;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.QuizStatus;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.evaluation.DailyEvaluationRepository;
import com.codegym.aiplanning.repository.evaluation.QuizQuestionRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuestion;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyEvaluationPersistenceService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final UserAccountRepository userAccountRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final DailyEvaluationRepository dailyEvaluationRepository;
    private final ObjectMapper objectMapper;

    public DailyEvaluationPersistenceService(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanVersionRepository dailyPlanVersionRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            ProgressEntryRepository progressEntryRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            UserAccountRepository userAccountRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            DailyEvaluationRepository dailyEvaluationRepository,
            ObjectMapper objectMapper) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.userAccountRepository = userAccountRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dailyEvaluationRepository = dailyEvaluationRepository;
        this.objectMapper = objectMapper;
    }

    public record DailyQuizContext(
            DailyPlan dailyPlan,
            Roadmap roadmap,
            List<UUID> completedTopicItemIds
    ) {}

    @Transactional(readOnly = true)
    public DailyQuizContext prepareDailyQuizContext(UUID userId, UUID dailyPlanId) {
        DailyPlan dailyPlan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Daily plan not found."));

        UUID roadmapId = dailyPlan.getRoadmapId();
        if (roadmapId == null) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                    "Kế hoạch ngày cần liên kết với một Lộ trình để sinh bài kiểm tra.");
        }

        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found."));

        UUID versionId = dailyPlan.getActiveVersionId();
        if (versionId == null) {
            versionId = dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(dailyPlanId)
                    .map(DailyPlanVersion::getId)
                    .orElse(null);
        }

        if (versionId == null) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_COMPLETED_TASKS,
                    "Cần có ít nhất một phiên bản kế hoạch ngày để sinh bài kiểm tra.");
        }

        List<DailyPlanItem> items = dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId);
        if (items.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_COMPLETED_TASKS,
                    "Cần hoàn thành ít nhất một nhiệm vụ trong ngày để sinh bài kiểm tra.");
        }

        List<UUID> itemIds = items.stream().map(DailyPlanItem::getId).toList();
        List<ProgressEntry> progressEntries = progressEntryRepository.findByDailyPlanItemIdInOrderByRecordedAtDesc(itemIds);

        Map<UUID, ProgressEntryStatus> latestProgressMap = new HashMap<>();
        for (ProgressEntry entry : progressEntries) {
            if (entry.getDailyPlanItemId() != null && !latestProgressMap.containsKey(entry.getDailyPlanItemId())) {
                latestProgressMap.put(entry.getDailyPlanItemId(), entry.getStatus());
            }
        }

        Set<UUID> completedRoadmapItemIds = new HashSet<>();
        for (DailyPlanItem item : items) {
            ProgressEntryStatus status = latestProgressMap.get(item.getId());
            boolean isCompleted = status == ProgressEntryStatus.COMPLETED
                    || (status == null && item.getStatus() == DailyTaskStatus.COMPLETED);

            if (isCompleted && item.getRoadmapItemId() != null) {
                completedRoadmapItemIds.add(item.getRoadmapItemId());
            }
        }

        if (completedRoadmapItemIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_COMPLETED_TASKS,
                    "Cần hoàn thành ít nhất một nhiệm vụ trong ngày để sinh bài kiểm tra.");
        }

        return new DailyQuizContext(dailyPlan, roadmap, new ArrayList<>(completedRoadmapItemIds));
    }

    @Transactional(readOnly = true)
    public Optional<QuizDetailResponse> findExistingGeneratedQuiz(UUID userId, UUID dailyPlanId) {
        List<Quiz> quizzes = quizRepository.findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                userId, dailyPlanId, QuizType.DAILY_MICRO_QUIZ);
        return quizzes.stream()
                .filter(q -> q.getStatus() == QuizStatus.GENERATED)
                .findFirst()
                .map(this::mapToQuizDetailResponse);
    }

    @Transactional(readOnly = true)
    public Optional<QuizDetailResponse> findLatestDailyQuiz(UUID userId, UUID dailyPlanId) {
        List<Quiz> quizzes = quizRepository.findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                userId, dailyPlanId, QuizType.DAILY_MICRO_QUIZ);
        if (quizzes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToQuizDetailResponse(quizzes.get(0)));
    }

    @Transactional(readOnly = true)
    public List<QuizDetailResponse> getAllDailyQuizzes(UUID userId, UUID dailyPlanId) {
        return quizRepository.findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                userId, dailyPlanId, QuizType.DAILY_MICRO_QUIZ).stream()
                .map(this::mapToQuizDetailResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getDailyQuizById(UUID userId, UUID dailyPlanId, UUID quizId) {
        Quiz quiz = quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found."));
        if (!dailyPlanId.equals(quiz.getDailyPlan() != null ? quiz.getDailyPlan().getId() : null)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Quiz does not belong to this daily plan.");
        }
        return mapToQuizDetailResponse(quiz);
    }

    @Transactional
    public QuizDetailResponse saveGeneratedDailyQuiz(
            UUID userId, UUID dailyPlanId, UUID roadmapId, GeneratedQuizPlan generatedPlan) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
        DailyPlan dailyPlan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Daily plan not found."));
        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found."));

        // If an unsubmitted (GENERATED) quiz already exists, return it
        List<Quiz> existingQuizzes = quizRepository.findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                userId, dailyPlanId, QuizType.DAILY_MICRO_QUIZ);
        Optional<Quiz> unsubmitted = existingQuizzes.stream()
                .filter(q -> q.getStatus() == QuizStatus.GENERATED)
                .findFirst();
        if (unsubmitted.isPresent()) {
            return mapToQuizDetailResponse(unsubmitted.get());
        }

        Quiz quiz = Quiz.createDailyMicroQuiz(user, dailyPlan, roadmap);

        // Pre-fetch roadmap items in a single query (0 N+1)
        List<UUID> topicIds = generatedPlan.questions().stream()
                .map(GeneratedQuestion::roadmapItemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, RoadmapItem> roadmapItemMap = new HashMap<>();
        if (!topicIds.isEmpty()) {
            roadmapItemRepository.findAllById(topicIds).forEach(item -> roadmapItemMap.put(item.getId(), item));
        }

        for (GeneratedQuestion q : generatedPlan.questions()) {
            RoadmapItem roadmapItem = q.roadmapItemId() != null ? roadmapItemMap.get(q.roadmapItemId()) : null;
            String optionsJson = serializeOptions(q.options());
            QuizQuestion question = QuizQuestion.create(
                    roadmapItem,
                    q.questionText(),
                    optionsJson,
                    q.correctOption(),
                    q.explanation(),
                    q.orderIndex());
            quiz.addQuestion(question);
        }

        Quiz saved = quizRepository.saveAndFlush(quiz);
        return mapToQuizDetailResponse(saved);
    }

    @Transactional
    public QuizDetailResponse submitDailyQuiz(
            UUID userId, UUID dailyPlanId, UUID quizId, SubmitQuizRequest request) {
        Quiz quiz = quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found."));

        if (!dailyPlanId.equals(quiz.getDailyPlan() != null ? quiz.getDailyPlan().getId() : null)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Quiz does not belong to this daily plan.");
        }

        if (quiz.getStatus() == QuizStatus.SUBMITTED) {
            throw new BusinessException(
                    ErrorCode.QUIZ_ALREADY_SUBMITTED,
                    "Bài kiểm tra này đã được nộp trước đó.");
        }

        Map<UUID, String> answerMap = new HashMap<>();
        if (request != null && request.answers() != null) {
            for (AnswerSubmissionDto answerDto : request.answers()) {
                if (answerDto.questionId() != null && answerDto.selectedOption() != null) {
                    answerMap.put(answerDto.questionId(), answerDto.selectedOption().trim().toUpperCase());
                }
            }
        }

        int correctCount = 0;
        List<QuizQuestion> questions = quiz.getQuestions();
        for (QuizQuestion q : questions) {
            String selected = answerMap.get(q.getId());
            q.answer(selected != null ? selected : "");
            if (Boolean.TRUE.equals(q.getIsCorrect())) {
                correctCount++;
            }
        }

        int totalCount = questions.isEmpty() ? 1 : questions.size();
        BigDecimal score = BigDecimal.valueOf((double) correctCount * 100.0 / totalCount)
                .setScale(2, RoundingMode.HALF_UP);
        boolean passed = score.compareTo(BigDecimal.valueOf(80.0)) >= 0;

        quiz.completeSubmission(score, passed, Instant.now());
        quizRepository.save(quiz);

        // Update DailyEvaluation
        DailyEvaluation evaluation = dailyEvaluationRepository.findByUserIdAndDailyPlanId(userId, dailyPlanId)
                .orElseGet(() -> DailyEvaluation.create(
                        quiz.getUser(),
                        quiz.getDailyPlan(),
                        quiz.getDailyPlan().getPlanDate(),
                        null,
                        null,
                        null,
                        null));
        evaluation.updateQuizResult(score, passed);
        dailyEvaluationRepository.save(evaluation);

        return mapToQuizDetailResponse(quiz);
    }

    @Transactional(readOnly = true)
    public DailyEvaluationResponse getDailyEvaluation(UUID userId, UUID dailyPlanId) {
        return dailyEvaluationRepository.findByUserIdAndDailyPlanId(userId, dailyPlanId)
                .map(e -> new DailyEvaluationResponse(
                        e.getId(),
                        e.getDailyPlan().getId(),
                        e.getEvaluationDate(),
                        e.getQuizScore(),
                        e.getQuizPassed(),
                        e.getOverallRating(),
                        e.getFeedbackNote(),
                        e.getCreatedAt()))
                .orElse(null);
    }

    public QuizDetailResponse mapToQuizDetailResponse(Quiz quiz) {
        boolean isSubmitted = quiz.getStatus() == QuizStatus.SUBMITTED;

        List<QuizQuestionResponse> questionResponses = quiz.getQuestions().stream()
                .map(q -> new QuizQuestionResponse(
                        q.getId(),
                        q.getRoadmapItem() != null ? q.getRoadmapItem().getId() : null,
                        q.getQuestionText(),
                        deserializeOptions(q.getOptionsJson()),
                        isSubmitted ? q.getCorrectOption() : null,
                        isSubmitted ? q.getUserAnswer() : null,
                        isSubmitted ? q.getIsCorrect() : null,
                        isSubmitted ? q.getExplanation() : null))
                .toList();

        return new QuizDetailResponse(
                quiz.getId(),
                quiz.getDailyPlan() != null ? quiz.getDailyPlan().getId() : null,
                quiz.getRoadmap().getId(),
                quiz.getQuizType(),
                quiz.getStatus(),
                isSubmitted ? quiz.getScore() : null,
                isSubmitted ? quiz.getPassed() : null,
                quiz.getSubmittedAt(),
                questionResponses);
    }

    private String serializeOptions(List<QuizOptionDto> options) {
        try {
            return objectMapper.writeValueAsString(options != null ? options : Collections.emptyList());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize quiz options.");
        }
    }

    private List<QuizOptionDto> deserializeOptions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuizOptionDto>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}

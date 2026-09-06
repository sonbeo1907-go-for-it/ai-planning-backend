package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.AnswerSubmissionDto;
import com.codegym.aiplanning.controller.evaluation.dto.DailyEvaluationResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto;
import com.codegym.aiplanning.controller.evaluation.dto.QuizQuestionResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.evaluation.DailyEvaluation;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizAttempt;
import com.codegym.aiplanning.entity.evaluation.QuizAttemptAnswer;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.QuizStatus;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.evaluation.DailyEvaluationRepository;
import com.codegym.aiplanning.repository.evaluation.QuizAttemptRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuestion;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.codegym.aiplanning.service.evaluation.QuizTopicScoredEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyEvaluationPersistenceService {

    private static final BigDecimal PASSING_SCORE = new BigDecimal("80.00");

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanVersionRepository dailyPlanVersionRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final UserAccountRepository userAccountRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final DailyEvaluationRepository dailyEvaluationRepository;
    private final ApplicationEventPublisher eventPublisher;
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
            QuizAttemptRepository quizAttemptRepository,
            DailyEvaluationRepository dailyEvaluationRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanVersionRepository = dailyPlanVersionRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.userAccountRepository = userAccountRepository;
        this.quizRepository = quizRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.dailyEvaluationRepository = dailyEvaluationRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public record DailyQuizContext(
            DailyPlan dailyPlan,
            DailyPlanVersion dailyPlanVersion,
            Roadmap roadmap,
            RoadmapVersion roadmapVersion,
            List<UUID> completedTopicItemIds) {}

    @Transactional(readOnly = true)
    public DailyQuizContext prepareDailyQuizContext(
            UUID userId,
            UUID dailyPlanVersionId) {
        DailyPlanVersion version = dailyPlanVersionRepository.findById(dailyPlanVersionId)
                .orElseThrow(() -> notFound("Daily Plan version was not found."));
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(version.getDailyPlanId(), userId)
                .orElseThrow(() -> notFound("Daily Plan version was not found."));
        if (!dailyPlanVersionId.equals(plan.getActiveVersionId())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Only the active Daily Plan version can generate a daily quiz.");
        }
        Roadmap roadmap = roadmapRepository.findByIdAndOwnerId(plan.getRoadmapId(), userId)
                .orElseThrow(() -> notFound("Roadmap was not found."));

        List<UUID> completedTopicIds = findCompletedTopicIds(version.getId());
        RoadmapVersion roadmapVersion = validateCompletedTopics(
                userId,
                roadmap,
                completedTopicIds);
        return new DailyQuizContext(
                plan,
                version,
                roadmap,
                roadmapVersion,
                completedTopicIds);
    }

    @Transactional(readOnly = true)
    public UUID resolveActiveVersionId(UUID userId, UUID dailyPlanId) {
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, userId)
                .orElseThrow(() -> notFound("Daily Plan was not found."));
        if (plan.getActiveVersionId() == null) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_COMPLETED_TASKS,
                    "Activate a Daily Plan version before generating a quiz.");
        }
        return plan.getActiveVersionId();
    }

    private List<UUID> findCompletedTopicIds(UUID versionId) {
        List<DailyPlanItem> items = dailyPlanItemRepository
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId);
        if (items.isEmpty()) {
            throw insufficientCompletedTasks();
        }

        List<UUID> itemIds = items.stream()
                .map(DailyPlanItem::getId)
                .toList();
        List<ProgressEntry> entries = progressEntryRepository
                .findByDailyPlanItemIdInOrderByRecordedAtDesc(itemIds);
        Map<UUID, ProgressEntryStatus> latestStatus = new HashMap<>();
        for (ProgressEntry entry : entries) {
            if (entry.getDailyPlanItemId() != null) {
                latestStatus.putIfAbsent(
                        entry.getDailyPlanItemId(),
                        entry.getStatus());
            }
        }

        Set<UUID> topicIds = new HashSet<>();
        for (DailyPlanItem item : items) {
            ProgressEntryStatus status = latestStatus.get(item.getId());
            boolean completed = status == ProgressEntryStatus.COMPLETED
                    || (status == null
                    && item.getStatus() == DailyTaskStatus.COMPLETED);
            if (completed && item.getRoadmapItemId() != null) {
                topicIds.add(item.getRoadmapItemId());
            }
        }
        if (topicIds.isEmpty()) {
            throw insufficientCompletedTasks();
        }
        return new ArrayList<>(topicIds);
    }

    private RoadmapVersion validateCompletedTopics(
            UUID userId,
            Roadmap roadmap,
            List<UUID> topicIds) {
        RoadmapVersion expectedVersion = null;
        for (UUID topicId : topicIds) {
            RoadmapItem item = roadmapItemRepository.findOwnedById(topicId, userId)
                    .orElseThrow(() -> notFound("Completed Roadmap topic was not found."));
            if (item.getItemType() != RoadmapItemType.TOPIC
                    || !item.getRoadmapVersion().getRoadmap().getId().equals(roadmap.getId())) {
                throw new BusinessException(
                        ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                        "Completed tasks must reference topics from the Daily Plan Roadmap.");
            }
            if (expectedVersion == null) {
                expectedVersion = item.getRoadmapVersion();
            } else if (!expectedVersion.getId().equals(item.getRoadmapVersion().getId())) {
                throw new BusinessException(
                        ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                        "A Daily Plan quiz cannot mix topics from different Roadmap versions.");
            }
        }
        return expectedVersion;
    }

    @Transactional(readOnly = true)
    public Optional<QuizDetailResponse> findExistingGeneratedQuiz(
            UUID userId,
            UUID dailyPlanVersionId) {
        return quizRepository
                .findWithQuestionsByUserIdAndDailyPlanVersionIdAndQuizType(
                        userId,
                        dailyPlanVersionId,
                        QuizType.DAILY_MICRO_QUIZ)
                .stream()
                .filter(quiz -> quiz.getStatus() == QuizStatus.GENERATED)
                .findFirst()
                .map(this::mapToQuizDetailResponse);
    }

    @Transactional(readOnly = true)
    public Optional<QuizDetailResponse> findLatestDailyQuiz(
            UUID userId,
            UUID dailyPlanId) {
        List<Quiz> quizzes = quizRepository
                .findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                        userId,
                        dailyPlanId,
                        QuizType.DAILY_MICRO_QUIZ);
        if (quizzes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToQuizDetailResponse(quizzes.get(0)));
    }

    @Transactional(readOnly = true)
    public List<QuizDetailResponse> getAllDailyQuizzes(
            UUID userId,
            UUID dailyPlanId) {
        return quizRepository
                .findWithQuestionsByUserIdAndDailyPlanIdAndQuizType(
                        userId,
                        dailyPlanId,
                        QuizType.DAILY_MICRO_QUIZ)
                .stream()
                .map(this::mapToQuizDetailResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getDailyQuizById(
            UUID userId,
            UUID dailyPlanId,
            UUID quizId) {
        Quiz quiz = getOwnedQuizForUpdate(quizId, userId);
        if (quiz.getDailyPlan() == null
                || !dailyPlanId.equals(quiz.getDailyPlan().getId())) {
            throw notFound("Quiz was not found.");
        }
        return mapToQuizDetailResponse(quiz);
    }

    @Transactional
    public QuizDetailResponse saveGeneratedDailyQuiz(
            UUID userId,
            DailyQuizContext context,
            GeneratedQuizPlan generatedPlan) {
        Optional<QuizDetailResponse> existing = findExistingGeneratedQuiz(
                userId,
                context.dailyPlanVersion().getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> notFound("User was not found."));
        Map<UUID, RoadmapItem> allowedTopics = loadAllowedTopics(
                userId,
                context,
                generatedPlan);
        Quiz quiz = Quiz.createDailyMicroQuiz(
                user,
                context.dailyPlan(),
                context.dailyPlanVersion(),
                context.roadmap(),
                context.roadmapVersion());
        addQuestions(quiz, generatedPlan, allowedTopics);
        return mapToQuizDetailResponse(quizRepository.saveAndFlush(quiz));
    }

    private Map<UUID, RoadmapItem> loadAllowedTopics(
            UUID userId,
            DailyQuizContext context,
            GeneratedQuizPlan generatedPlan) {
        Set<UUID> allowedIds = new HashSet<>(context.completedTopicItemIds());
        Map<UUID, RoadmapItem> result = new HashMap<>();
        for (GeneratedQuestion question : generatedPlan.questions()) {
            UUID itemId = question.roadmapItemId();
            if (itemId == null || !allowedIds.contains(itemId)) {
                throw new BusinessException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "AI quiz referenced a topic outside the completed-topic context.");
            }
            RoadmapItem item = roadmapItemRepository.findOwnedById(itemId, userId)
                    .orElseThrow(() -> notFound("Roadmap topic was not found."));
            if (!context.roadmapVersion().getId()
                    .equals(item.getRoadmapVersion().getId())) {
                throw new BusinessException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "AI quiz referenced a topic from another Roadmap version.");
            }
            result.put(itemId, item);
        }
        return result;
    }

    private void addQuestions(
            Quiz quiz,
            GeneratedQuizPlan generatedPlan,
            Map<UUID, RoadmapItem> topics) {
        for (GeneratedQuestion generated : generatedPlan.questions()) {
            QuizQuestion question = QuizQuestion.create(
                    topics.get(generated.roadmapItemId()),
                    generated.questionText(),
                    serializeOptions(generated.options()),
                    generated.correctOption(),
                    generated.explanation(),
                    generated.orderIndex());
            quiz.addQuestion(question);
        }
    }

    @Transactional
    public QuizDetailResponse submitDailyQuiz(
            UUID userId,
            UUID dailyPlanId,
            UUID quizId,
            SubmitQuizRequest request) {
        Quiz quiz = getOwnedQuizForUpdate(quizId, userId);
        if (quiz.getDailyPlan() == null
                || !dailyPlanId.equals(quiz.getDailyPlan().getId())) {
            throw notFound("Quiz was not found.");
        }
        if (quiz.getStatus() == QuizStatus.SUBMITTED) {
            throw new BusinessException(
                    ErrorCode.QUIZ_ALREADY_SUBMITTED,
                    "This quiz was already submitted.");
        }

        Map<UUID, String> answers = validateAnswers(quiz, request);
        QuizAttempt attempt = gradeAttempt(quiz, answers);
        quiz.markSubmitted();
        quizRepository.save(quiz);
        quizAttemptRepository.saveAndFlush(attempt);
        updateDailyEvaluation(quiz, attempt);
        updateWeakTopics(userId, quiz, attempt);
        return mapToQuizDetailResponse(quiz, attempt);
    }

    @Transactional
    public QuizDetailResponse submitMasteryQuiz(
            UUID userId,
            UUID weakTopicId,
            UUID quizId,
            SubmitQuizRequest request) {
        Quiz quiz = getOwnedQuizForUpdate(quizId, userId);
        if (quiz.getTargetWeakTopic() == null
                || !weakTopicId.equals(quiz.getTargetWeakTopic().getId())
                || quiz.getQuizType() != QuizType.MASTERY_CHECK) {
            throw notFound("Mastery quiz was not found.");
        }
        if (quiz.getStatus() == QuizStatus.SUBMITTED) {
            return mapToQuizDetailResponse(quiz);
        }
        Map<UUID, String> answers = validateAnswers(quiz, request);
        QuizAttempt attempt = gradeAttempt(quiz, answers);
        quiz.markSubmitted();
        quizRepository.save(quiz);
        quizAttemptRepository.saveAndFlush(attempt);
        return mapToQuizDetailResponse(quiz, attempt);
    }

    private Map<UUID, String> validateAnswers(
            Quiz quiz,
            SubmitQuizRequest request) {
        List<AnswerSubmissionDto> submitted = request.answers();
        if (submitted.size() != quiz.getQuestions().size()) {
            throw new BusinessException(
                    ErrorCode.QUIZ_ANSWERS_INCOMPLETE,
                    "Every quiz question must be answered exactly once.");
        }

        Set<UUID> questionIds = quiz.getQuestions().stream()
                .map(QuizQuestion::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> answers = new HashMap<>();
        for (AnswerSubmissionDto answer : submitted) {
            if (!questionIds.contains(answer.questionId())) {
                throw new BusinessException(
                        ErrorCode.QUIZ_QUESTION_NOT_FOUND,
                        "An answer referenced a question outside this quiz.");
            }
            String option = answer.selectedOption().trim().toUpperCase();
            if (!Set.of("A", "B", "C", "D").contains(option)) {
                throw new BusinessException(
                        ErrorCode.QUIZ_OPTION_INVALID,
                        "Selected options must be A, B, C, or D.");
            }
            if (answers.put(answer.questionId(), option) != null) {
                throw new BusinessException(
                        ErrorCode.QUIZ_ANSWER_DUPLICATE,
                        "A quiz question cannot be answered more than once.");
            }
        }
        return answers;
    }

    private QuizAttempt gradeAttempt(
            Quiz quiz,
            Map<UUID, String> submittedAnswers) {
        int correctCount = 0;
        List<QuizAttemptAnswer> gradedAnswers = new ArrayList<>();
        for (QuizQuestion question : quiz.getQuestions()) {
            QuizAttemptAnswer answer = QuizAttemptAnswer.create(
                    question,
                    submittedAnswers.get(question.getId()));
            gradedAnswers.add(answer);
            if (answer.isCorrect()) {
                correctCount++;
            }
        }

        BigDecimal score = BigDecimal
                .valueOf((double) correctCount * 100.0 / quiz.getQuestions().size())
                .setScale(2, RoundingMode.HALF_UP);
        QuizAttempt attempt = QuizAttempt.create(
                quiz,
                quiz.getUser(),
                1,
                score,
                score.compareTo(PASSING_SCORE) >= 0,
                Instant.now());
        gradedAnswers.forEach(attempt::addAnswer);
        return attempt;
    }

    private void updateDailyEvaluation(
            Quiz quiz,
            QuizAttempt attempt) {
        DailyEvaluation evaluation = dailyEvaluationRepository
                .findByUserIdAndDailyPlanVersionId(
                        quiz.getUser().getId(),
                        quiz.getDailyPlanVersion().getId())
                .orElseGet(() -> DailyEvaluation.create(
                        quiz.getUser(),
                        quiz.getDailyPlan(),
                        quiz.getDailyPlanVersion(),
                        quiz.getDailyPlan().getPlanDate(),
                        null,
                        null,
                        null,
                        null));
        evaluation.updateQuizResult(
                attempt.getScore(),
                attempt.isPassed());
        dailyEvaluationRepository.save(evaluation);
    }

    private void updateWeakTopics(
            UUID userId,
            Quiz quiz,
            QuizAttempt attempt) {
        Map<UUID, int[]> perTopic = new HashMap<>();
        for (QuizAttemptAnswer answer : attempt.getAnswers()) {
            UUID topicId = answer.getQuestion().getRoadmapItem().getId();
            int[] counts = perTopic.computeIfAbsent(topicId, ignored -> new int[2]);
            counts[1]++;
            if (answer.isCorrect()) {
                counts[0]++;
            }
        }
        for (Map.Entry<UUID, int[]> entry : perTopic.entrySet()) {
            int[] counts = entry.getValue();
            BigDecimal score = BigDecimal
                    .valueOf((double) counts[0] * 100.0 / counts[1])
                    .setScale(2, RoundingMode.HALF_UP);
            eventPublisher.publishEvent(new QuizTopicScoredEvent(
                    userId,
                    quiz.getRoadmap().getId(),
                    entry.getKey(),
                    score));
        }
    }

    @Transactional(readOnly = true)
    public DailyEvaluationResponse getDailyEvaluation(
            UUID userId,
            UUID dailyPlanId) {
        UUID versionId = resolveActiveVersionId(userId, dailyPlanId);
        return dailyEvaluationRepository
                .findByUserIdAndDailyPlanVersionId(userId, versionId)
                .map(this::mapEvaluation)
                .orElse(null);
    }

    public QuizDetailResponse mapToQuizDetailResponse(Quiz quiz) {
        QuizAttempt attempt = quizAttemptRepository
                .findFirstByQuizIdAndUserIdOrderByAttemptNumberDesc(
                        quiz.getId(),
                        quiz.getUser().getId())
                .orElse(null);
        return mapToQuizDetailResponse(quiz, attempt);
    }

    private QuizDetailResponse mapToQuizDetailResponse(
            Quiz quiz,
            QuizAttempt attempt) {
        Map<UUID, QuizAttemptAnswer> answers = new HashMap<>();
        if (attempt != null) {
            for (QuizAttemptAnswer answer : attempt.getAnswers()) {
                answers.put(answer.getQuestion().getId(), answer);
            }
        }
        boolean submitted = attempt != null;
        List<QuizQuestionResponse> questions = quiz.getQuestions().stream()
                .map(question -> mapQuestion(question, answers.get(question.getId()), submitted))
                .toList();
        return new QuizDetailResponse(
                quiz.getId(),
                quiz.getDailyPlan() == null ? null : quiz.getDailyPlan().getId(),
                quiz.getDailyPlanVersion() == null
                        ? null
                        : quiz.getDailyPlanVersion().getId(),
                quiz.getRoadmap().getId(),
                quiz.getQuizType(),
                quiz.getStatus(),
                submitted ? attempt.getScore() : null,
                submitted ? attempt.isPassed() : null,
                submitted ? attempt.getSubmittedAt() : null,
                questions);
    }

    private QuizQuestionResponse mapQuestion(
            QuizQuestion question,
            QuizAttemptAnswer answer,
            boolean submitted) {
        return new QuizQuestionResponse(
                question.getId(),
                question.getRoadmapItem().getId(),
                question.getQuestionText(),
                deserializeOptions(question.getOptionsJson()),
                submitted ? question.getCorrectOption() : null,
                answer == null ? null : answer.getSelectedOption(),
                answer == null ? null : answer.isCorrect(),
                submitted ? question.getExplanation() : null);
    }

    private DailyEvaluationResponse mapEvaluation(DailyEvaluation evaluation) {
        return new DailyEvaluationResponse(
                evaluation.getId(),
                evaluation.getDailyPlan().getId(),
                evaluation.getDailyPlanVersion().getId(),
                evaluation.getEvaluationDate(),
                evaluation.getQuizScore(),
                evaluation.getQuizPassed(),
                evaluation.getOverallRating(),
                evaluation.getFeedbackNote(),
                evaluation.getCreatedAt());
    }

    private Quiz getOwnedQuiz(UUID quizId, UUID userId) {
        return quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.QUIZ_NOT_FOUND,
                        "Quiz was not found."));
    }

    private Quiz getOwnedQuizForUpdate(UUID quizId, UUID userId) {
        return quizRepository.findWithQuestionsByIdAndUserIdForUpdate(quizId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.QUIZ_NOT_FOUND,
                        "Quiz was not found."));
    }

    private String serializeOptions(List<QuizOptionDto> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Quiz options could not be serialized.");
        }
    }

    private List<QuizOptionDto> deserializeOptions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<QuizOptionDto>>() {});
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }

    private BusinessException insufficientCompletedTasks() {
        return new BusinessException(
                ErrorCode.INSUFFICIENT_COMPLETED_TASKS,
                "Complete at least one Roadmap-linked task before generating a quiz.");
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}

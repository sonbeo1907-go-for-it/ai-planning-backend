package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.Quiz;
import com.codegym.aiplanning.entity.evaluation.QuizQuestion;
import com.codegym.aiplanning.entity.evaluation.QuizStatus;
import com.codegym.aiplanning.entity.evaluation.QuizType;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.codegym.aiplanning.service.evaluation.QuizTopicScoredEvent;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class WeakTopicServiceImpl implements WeakTopicService, WeakTopicContextResolver {

    private static final BigDecimal QUIZ_SCORE_THRESHOLD = new BigDecimal("80.00");
    private static final int RATING_THRESHOLD = 2;

    private final WeakTopicRepository weakTopicRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapRepository roadmapRepository;
    private final QuizRepository quizRepository;
    private final QuizGeneratorService quizGeneratorService;
    private final DailyEvaluationPersistenceService evaluationPersistenceService;
    private final ObjectMapper objectMapper;

    public WeakTopicServiceImpl(
            WeakTopicRepository weakTopicRepository,
            UserAccountRepository userAccountRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapRepository roadmapRepository,
            QuizRepository quizRepository,
            QuizGeneratorService quizGeneratorService,
            DailyEvaluationPersistenceService evaluationPersistenceService,
            ObjectMapper objectMapper) {
        this.weakTopicRepository = weakTopicRepository;
        this.userAccountRepository = userAccountRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapRepository = roadmapRepository;
        this.quizRepository = quizRepository;
        this.quizGeneratorService = quizGeneratorService;
        this.evaluationPersistenceService = evaluationPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void processEvaluationResult(
            UUID userId,
            UUID roadmapId,
            UUID roadmapItemId,
            BigDecimal quizScore,
            Integer understandingRating) {

        boolean isLowScore = quizScore != null && quizScore.compareTo(QUIZ_SCORE_THRESHOLD) < 0;
        boolean isLowRating = understandingRating != null && understandingRating <= RATING_THRESHOLD;

        if (!isLowScore && !isLowRating) {
            return;
        }

        WeakTopicTrigger triggerSource = WeakTopicTrigger.BOTH;
        if (isLowScore && !isLowRating) {
            triggerSource = WeakTopicTrigger.QUIZ_FAILED;
        } else if (!isLowScore) {
            triggerSource = WeakTopicTrigger.LOW_RATING;
        }

        Optional<WeakTopic> existingWeakTopicOpt = weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId);

        if (existingWeakTopicOpt.isPresent()) {
            WeakTopic existingWeakTopic = existingWeakTopicOpt.get();
            if (!existingWeakTopic.getRoadmap().getId().equals(roadmapId)) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap Learning Unit was not found.");
            }
            requireLearningUnitHierarchy(existingWeakTopic.getRoadmapItem());
            existingWeakTopic.updateTrigger(triggerSource, quizScore, understandingRating, Instant.now());
            weakTopicRepository.save(existingWeakTopic);
        } else {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
            RoadmapItem roadmapItem = roadmapItemRepository.findOwnedById(roadmapItemId, userId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Roadmap Learning Unit was not found."));
            Roadmap roadmap = roadmapItem.getRoadmapVersion().getRoadmap();
            if (!roadmap.getId().equals(roadmapId)) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap Learning Unit was not found.");
            }
            requireLearningUnitHierarchy(roadmapItem);

            WeakTopic newWeakTopic = WeakTopic.create(
                    user,
                    roadmap,
                    roadmapItem.getRoadmapVersion(),
                    roadmapItem,
                    triggerSource,
                    quizScore,
                    understandingRating,
                    Instant.now());
            weakTopicRepository.save(newWeakTopic);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleQuizTopicScored(QuizTopicScoredEvent event) {
        processEvaluationResult(
                event.userId(),
                event.roadmapId(),
                event.roadmapItemId(),
                event.score(),
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicPromptContext> resolveUnresolvedWeakTopics(
            UUID userId,
            UUID roadmapVersionId) {
        List<WeakTopic> unresolvedTopics = weakTopicRepository
                .findWithItemByUserIdAndRoadmapVersionIdAndStatusIn(
                        userId,
                        roadmapVersionId,
                        Collections.singletonList(WeakTopicStatus.UNRESOLVED));

        return unresolvedTopics.stream()
                .map(wt -> {
                    LearningTargetHierarchy hierarchy = hierarchyOf(wt.getRoadmapItem());
                    return new WeakTopicPromptContext(
                            wt.getId(),
                            wt.getRoadmapItem().getId(),
                            wt.getRoadmapItem().getItemType(),
                            hierarchy.learningUnitId(),
                            hierarchy.learningUnitTitle(),
                            hierarchy.topicId(),
                            hierarchy.topicTitle(),
                            hierarchy.milestoneId(),
                            hierarchy.milestoneTitle(),
                            wt.getLastUnderstandingRating(),
                            wt.getLastQuizScore() != null ? wt.getLastQuizScore().doubleValue() : null
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicResponse> getWeakTopics(UUID userId, UUID roadmapId, Set<WeakTopicStatus> statuses) {
        roadmapRepository.findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap was not found."));
        Collection<WeakTopicStatus> selectedStatuses = statuses == null || statuses.isEmpty()
                ? List.of(WeakTopicStatus.values())
                : statuses;
        return weakTopicRepository
                .findWithItemByUserIdAndRoadmapIdAndStatusIn(
                        userId,
                        roadmapId,
                        selectedStatuses)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public QuizDetailResponse generateMasteryCheckQuiz(UUID userId, UUID weakTopicId) {
        return generateMasteryCheckQuizWithProviderConfig(userId, weakTopicId, null);
    }

    @Override
    public QuizDetailResponse generateMasteryCheckQuizWithProviderConfig(
            UUID userId,
            UUID weakTopicId,
            AiProviderConfig providerConfig) {
        WeakTopic weakTopic = requireOwnedWeakTopic(userId, weakTopicId);
        if (weakTopic.getStatus() == WeakTopicStatus.MASTERED) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "A mastered topic does not require another mastery check.");
        }

        Optional<Quiz> existing = quizRepository
                .findWithQuestionsByUserIdAndTargetWeakTopicIdAndQuizType(
                        userId,
                        weakTopicId,
                        QuizType.MASTERY_CHECK)
                .stream()
                .filter(quiz -> quiz.getStatus() == QuizStatus.GENERATED)
                .findFirst();
        if (existing.isPresent()) {
            return evaluationPersistenceService.mapToQuizDetailResponse(existing.get());
        }

        GeneratedQuizPlan generated = quizGeneratorService.generateMasteryCheckQuestions(
                userId,
                weakTopicId,
                weakTopic.getRoadmapItem().getId(),
                providerConfig);
        Quiz quiz = Quiz.createMasteryCheck(
                weakTopic.getUser(),
                weakTopic.getRoadmap(),
                weakTopic.getRoadmapVersion(),
                weakTopic);
        for (QuizGeneratorService.GeneratedQuestion question : generated.questions()) {
            if (!weakTopic.getRoadmapItem().getId().equals(question.roadmapItemId())) {
                throw new BusinessException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Mastery quiz referenced another Roadmap topic.");
            }
            quiz.addQuestion(QuizQuestion.create(
                    weakTopic.getRoadmapItem(),
                    question.questionText(),
                    serializeOptions(question.options()),
                    question.correctOption(),
                    question.explanation(),
                    question.orderIndex()));
        }
        return evaluationPersistenceService.mapToQuizDetailResponse(
                quizRepository.saveAndFlush(quiz));
    }

    @Override
    @Transactional
    public MasteryCheckResultResponse submitMasteryCheck(UUID userId, UUID weakTopicId, UUID quizId, SubmitQuizRequest request) {
        WeakTopic weakTopic = requireOwnedWeakTopic(userId, weakTopicId);
        QuizDetailResponse quiz = evaluationPersistenceService.submitMasteryQuiz(
                userId,
                weakTopicId,
                quizId,
                request);
        boolean mastered = Boolean.TRUE.equals(quiz.passed());
        if (mastered) {
            weakTopic.markMastered(Instant.now());
        } else {
            weakTopic.updateTrigger(
                    WeakTopicTrigger.QUIZ_FAILED,
                    quiz.score(),
                    null,
                    Instant.now());
        }
        weakTopicRepository.save(weakTopic);
        return new MasteryCheckResultResponse(
                weakTopicId,
                quiz.score(),
                mastered,
                weakTopic.getStatus(),
                mastered
                        ? "The topic is now mastered."
                        : "The topic remains unresolved.");
    }

    @Override
    @Transactional
    public void markInReview(UUID userId, UUID weakTopicId) {
        WeakTopic weakTopic = weakTopicRepository.findByIdAndUserId(weakTopicId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WEAK_TOPIC_NOT_FOUND,
                        "Weak Topic was not found."));
        weakTopic.markInReview();
        weakTopicRepository.save(weakTopic);
    }

    @Override
    @Transactional
    public void markInReviewByRoadmapItem(UUID userId, UUID roadmapItemId) {
        weakTopicRepository.findByUserIdAndRoadmapItemId(userId, roadmapItemId)
                .ifPresent(weakTopic -> {
                    weakTopic.markInReview();
                    weakTopicRepository.save(weakTopic);
                });
    }

    private WeakTopicResponse toResponse(WeakTopic weakTopic) {
        RoadmapItem item = weakTopic.getRoadmapItem();
        LearningTargetHierarchy hierarchy = hierarchyOf(item);
        return new WeakTopicResponse(
                weakTopic.getId(),
                weakTopic.getRoadmap().getId(),
                weakTopic.getRoadmapVersion().getId(),
                item.getId(),
                item.getItemType(),
                hierarchy.learningUnitId(),
                hierarchy.learningUnitTitle(),
                hierarchy.topicId(),
                hierarchy.topicTitle(),
                hierarchy.milestoneId(),
                hierarchy.milestoneTitle(),
                weakTopic.getStatus(),
                weakTopic.getTriggerSource(),
                weakTopic.getLastQuizScore(),
                weakTopic.getLastUnderstandingRating(),
                weakTopic.getUnresolvedAt(),
                weakTopic.getMasteredAt());
    }

    private void requireLearningUnitHierarchy(RoadmapItem item) {
        if (item.getItemType() != RoadmapItemType.LEARNING_UNIT
                || item.getRoadmapVersion() == null) {
            throw invalidWeakTopicTarget();
        }
        RoadmapItem topic = item.getParent();
        RoadmapItem milestone = topic == null ? null : topic.getParent();
        UUID versionId = item.getRoadmapVersion().getId();
        boolean valid = topic != null
                && topic.getItemType() == RoadmapItemType.TOPIC
                && topic.getRoadmapVersion() != null
                && versionId.equals(topic.getRoadmapVersion().getId())
                && milestone != null
                && milestone.getItemType() == RoadmapItemType.MILESTONE
                && milestone.getRoadmapVersion() != null
                && versionId.equals(milestone.getRoadmapVersion().getId());
        if (!valid) {
            throw invalidWeakTopicTarget();
        }
    }

    private BusinessException invalidWeakTopicTarget() {
        return new BusinessException(
                ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                "Weak Topic signals must target a Learning Unit inside a Topic and Milestone.");
    }

    /**
     * Maps both the strict V2 Learning Unit target and any readable legacy Topic target.
     * Legacy rows are exposed with null Learning Unit fields and are never accepted for new
     * weakness signals.
     */
    private LearningTargetHierarchy hierarchyOf(RoadmapItem item) {
        if (item.getItemType() == RoadmapItemType.LEARNING_UNIT) {
            RoadmapItem topic = item.getParent();
            RoadmapItem milestone = topic == null ? null : topic.getParent();
            return new LearningTargetHierarchy(
                    item.getId(),
                    item.getTitle(),
                    topic == null ? null : topic.getId(),
                    topic == null ? null : topic.getTitle(),
                    milestone == null ? null : milestone.getId(),
                    milestone == null ? null : milestone.getTitle());
        }

        RoadmapItem milestone = item.getParent();
        return new LearningTargetHierarchy(
                null,
                null,
                item.getId(),
                item.getTitle(),
                milestone == null ? null : milestone.getId(),
                milestone == null ? null : milestone.getTitle());
    }

    private record LearningTargetHierarchy(
            UUID learningUnitId,
            String learningUnitTitle,
            UUID topicId,
            String topicTitle,
            UUID milestoneId,
            String milestoneTitle) {}

    private WeakTopic requireOwnedWeakTopic(UUID userId, UUID weakTopicId) {
        return weakTopicRepository.findWithContextByIdAndUserId(weakTopicId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WEAK_TOPIC_NOT_FOUND,
                        "Weak Topic was not found."));
    }

    private String serializeOptions(
            List<com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Quiz options could not be serialized.");
        }
    }
}

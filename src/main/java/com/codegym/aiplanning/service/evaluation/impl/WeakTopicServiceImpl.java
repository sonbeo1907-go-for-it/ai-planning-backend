package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto;
import com.codegym.aiplanning.controller.evaluation.dto.QuizQuestionResponse;
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
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.evaluation.QuizRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuestion;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeakTopicServiceImpl implements WeakTopicService, WeakTopicContextResolver {

    private static final BigDecimal QUIZ_SCORE_THRESHOLD = new BigDecimal("80.00");
    private static final int RATING_THRESHOLD = 2;

    private final WeakTopicRepository weakTopicRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final QuizRepository quizRepository;
    private final QuizGeneratorService quizGeneratorService;
    private final ObjectMapper objectMapper;

    public WeakTopicServiceImpl(
            WeakTopicRepository weakTopicRepository,
            UserAccountRepository userAccountRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            QuizRepository quizRepository,
            QuizGeneratorService quizGeneratorService,
            ObjectMapper objectMapper) {
        this.weakTopicRepository = weakTopicRepository;
        this.userAccountRepository = userAccountRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.quizRepository = quizRepository;
        this.quizGeneratorService = quizGeneratorService;
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
            existingWeakTopic.updateTrigger(triggerSource, quizScore, understandingRating, Instant.now());
            weakTopicRepository.save(existingWeakTopic);
        } else {
            UserAccount user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
            Roadmap roadmap = roadmapRepository.findById(roadmapId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found"));
            RoadmapItem roadmapItem = roadmapItemRepository.findById(roadmapItemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap Item not found"));

            WeakTopic newWeakTopic = WeakTopic.create(
                    user,
                    roadmap,
                    roadmapItem,
                    triggerSource,
                    quizScore,
                    understandingRating,
                    Instant.now());
            weakTopicRepository.save(newWeakTopic);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicPromptContext> resolveUnresolvedWeakTopics(UUID userId, UUID roadmapId) {
        List<WeakTopic> unresolvedTopics = weakTopicRepository.findWithItemByUserIdAndRoadmapIdAndStatusIn(
                userId, roadmapId, Collections.singletonList(WeakTopicStatus.UNRESOLVED));

        return unresolvedTopics.stream()
                .map(wt -> {
                    String milestoneTitle = wt.getRoadmapItem().getParent() != null
                            ? wt.getRoadmapItem().getParent().getTitle()
                            : null;
                    return new WeakTopicPromptContext(
                            wt.getId(),
                            wt.getRoadmapItem().getId(),
                            wt.getRoadmapItem().getTitle(),
                            milestoneTitle,
                            wt.getLastUnderstandingRating(),
                            wt.getLastQuizScore() != null ? wt.getLastQuizScore().doubleValue() : null
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicResponse> getWeakTopics(UUID userId, UUID roadmapId, Set<WeakTopicStatus> statuses) {
        List<WeakTopic> topics = statuses == null || statuses.isEmpty()
                ? weakTopicRepository.findByUserIdAndRoadmapIdOrderByCreatedAtDesc(userId, roadmapId)
                : weakTopicRepository.findByUserIdAndRoadmapIdAndStatusInOrderByCreatedAtDesc(
                        userId, roadmapId, statuses);
        return topics.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public QuizDetailResponse generateMasteryCheckQuiz(UUID userId, UUID weakTopicId) {
        WeakTopic weakTopic = findWeakTopic(userId, weakTopicId);
        Optional<QuizDetailResponse> existing = quizRepository
                .findWithQuestionsByUserIdAndTargetWeakTopicIdAndQuizType(
                        userId, weakTopicId, QuizType.MASTERY_CHECK)
                .stream()
                .filter(quiz -> quiz.getStatus() == QuizStatus.GENERATED)
                .findFirst()
                .map(this::mapToQuizDetailResponse);
        if (existing.isPresent()) {
            return existing.get();
        }

        GeneratedQuizPlan generatedPlan = quizGeneratorService.generateMasteryCheckQuestions(
                userId, weakTopicId, weakTopic.getRoadmapItem().getId());
        Quiz quiz = Quiz.createMasteryCheck(
                weakTopic.getUser(), null, weakTopic.getRoadmap(), weakTopic);
        for (GeneratedQuestion question : generatedPlan.questions()) {
            quiz.addQuestion(QuizQuestion.create(
                    weakTopic.getRoadmapItem(),
                    question.questionText(),
                    serializeOptions(question.options()),
                    question.correctOption(),
                    question.explanation(),
                    question.orderIndex()));
        }
        return mapToQuizDetailResponse(quizRepository.saveAndFlush(quiz));
    }

    @Override
    @Transactional
    public MasteryCheckResultResponse submitMasteryCheck(UUID userId, UUID weakTopicId, UUID quizId, SubmitQuizRequest request) {
        WeakTopic weakTopic = findWeakTopic(userId, weakTopicId);
        Quiz quiz = quizRepository.findWithQuestionsByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found."));
        if (quiz.getQuizType() != QuizType.MASTERY_CHECK
                || quiz.getTargetWeakTopic() == null
                || !weakTopicId.equals(quiz.getTargetWeakTopic().getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Quiz does not belong to this weak topic.");
        }
        if (quiz.getStatus() == QuizStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED, "Bài kiểm tra này đã được nộp trước đó.");
        }

        Map<UUID, String> answers = new HashMap<>();
        if (request != null && request.answers() != null) {
            request.answers().forEach(answer -> {
                if (answer.questionId() != null && answer.selectedOption() != null) {
                    answers.put(answer.questionId(), answer.selectedOption().trim().toUpperCase());
                }
            });
        }
        int correctCount = 0;
        for (QuizQuestion question : quiz.getQuestions()) {
            question.answer(answers.getOrDefault(question.getId(), ""));
            if (Boolean.TRUE.equals(question.getIsCorrect())) {
                correctCount++;
            }
        }
        int totalCount = quiz.getQuestions().size();
        BigDecimal score = totalCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(correctCount * 100.0 / totalCount)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
        boolean mastered = totalCount > 0 && correctCount == totalCount;
        quiz.completeSubmission(score, mastered, Instant.now());
        quizRepository.save(quiz);
        if (mastered) {
            weakTopic.markMastered(Instant.now());
            weakTopicRepository.save(weakTopic);
        }
        return new MasteryCheckResultResponse(
                weakTopicId,
                score,
                mastered,
                weakTopic.getStatus(),
                mastered ? "Bạn đã làm chủ chủ đề này." : "Bạn cần ôn tập thêm chủ đề này.");
    }

    @Override
    @Transactional
    public void markInReview(UUID userId, UUID weakTopicId) {
        WeakTopic weakTopic = findWeakTopic(userId, weakTopicId);
        weakTopic.markInReview();
        weakTopicRepository.save(weakTopic);
    }

    private WeakTopic findWeakTopic(UUID userId, UUID weakTopicId) {
        return weakTopicRepository.findByIdAndUserId(weakTopicId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WEAK_TOPIC_NOT_FOUND, "Weak topic not found."));
    }

    private WeakTopicResponse mapToResponse(WeakTopic weakTopic) {
        RoadmapItem item = weakTopic.getRoadmapItem();
        return new WeakTopicResponse(
                weakTopic.getId(),
                weakTopic.getRoadmap().getId(),
                item.getId(),
                item.getTitle(),
                item.getParent() != null ? item.getParent().getTitle() : null,
                weakTopic.getStatus(),
                weakTopic.getTriggerSource(),
                weakTopic.getLastQuizScore(),
                weakTopic.getLastUnderstandingRating(),
                weakTopic.getUnresolvedAt(),
                weakTopic.getMasteredAt());
    }

    private QuizDetailResponse mapToQuizDetailResponse(Quiz quiz) {
        boolean submitted = quiz.getStatus() == QuizStatus.SUBMITTED;
        List<QuizQuestionResponse> questions = quiz.getQuestions().stream()
                .map(question -> new QuizQuestionResponse(
                        question.getId(),
                        question.getRoadmapItem() != null ? question.getRoadmapItem().getId() : null,
                        question.getQuestionText(),
                        deserializeOptions(question.getOptionsJson()),
                        submitted ? question.getCorrectOption() : null,
                        submitted ? question.getUserAnswer() : null,
                        submitted ? question.getIsCorrect() : null,
                        submitted ? question.getExplanation() : null))
                .toList();
        return new QuizDetailResponse(
                quiz.getId(),
                null,
                quiz.getRoadmap().getId(),
                quiz.getQuizType(),
                quiz.getStatus(),
                submitted ? quiz.getScore() : null,
                submitted ? quiz.getPassed() : null,
                quiz.getSubmittedAt(),
                questions);
    }

    private String serializeOptions(List<QuizOptionDto> options) {
        try {
            return objectMapper.writeValueAsString(options != null ? options : Collections.emptyList());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize quiz options.");
        }
    }

    private List<QuizOptionDto> deserializeOptions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuizOptionDto>>() {});
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }
}

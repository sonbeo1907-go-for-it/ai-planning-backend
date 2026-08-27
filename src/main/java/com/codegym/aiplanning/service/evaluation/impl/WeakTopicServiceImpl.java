package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.evaluation.dto.MasteryCheckResultResponse;
import com.codegym.aiplanning.controller.evaluation.dto.QuizDetailResponse;
import com.codegym.aiplanning.controller.evaluation.dto.SubmitQuizRequest;
import com.codegym.aiplanning.controller.evaluation.dto.WeakTopicResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.evaluation.WeakTopicContextResolver;
import com.codegym.aiplanning.service.evaluation.WeakTopicService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
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

    public WeakTopicServiceImpl(
            WeakTopicRepository weakTopicRepository,
            UserAccountRepository userAccountRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository) {
        this.weakTopicRepository = weakTopicRepository;
        this.userAccountRepository = userAccountRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
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
    public List<WeakTopicResponse> getWeakTopics(UUID userId, UUID roadmapId, Set<WeakTopicStatus> statuses) {
        return Collections.emptyList();
    }

    @Override
    public QuizDetailResponse generateMasteryCheckQuiz(UUID userId, UUID weakTopicId) {
        return null;
    }

    @Override
    public MasteryCheckResultResponse submitMasteryCheck(UUID userId, UUID weakTopicId, UUID quizId, SubmitQuizRequest request) {
        return null;
    }

    @Override
    public void markInReview(UUID userId, UUID weakTopicId) {
        // Minimal stub to satisfy interface
    }
}

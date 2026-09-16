package com.codegym.aiplanning.service.guidance;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.source.LearningSource;
import com.codegym.aiplanning.entity.source.LearningSourceStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanTaskStepRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.RoadmapContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.SourceContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.TaskStepSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskGuidanceContextBuilder {

    private static final int MAX_SOURCE_COUNT = 6;
    private static final int MAX_SOURCE_CHARACTERS = 8_000;
    private static final int MAX_CHARACTERS_PER_SOURCE = 2_000;

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanTaskStepRepository taskStepRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;
    private final UserProfileRepository userProfileRepository;

    public TaskGuidanceContextBuilder(
            DailyPlanRepository dailyPlanRepository,
            DailyPlanItemRepository dailyPlanItemRepository,
            DailyPlanTaskStepRepository taskStepRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapSourceRepository roadmapSourceRepository,
            UserProfileRepository userProfileRepository) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.dailyPlanItemRepository = dailyPlanItemRepository;
        this.taskStepRepository = taskStepRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public TaskGuidanceContext build(
            UUID ownerId,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId) {
        DailyPlan plan = dailyPlanRepository.findByIdAndUserId(dailyPlanId, ownerId)
                .orElseThrow(this::itemNotFound);
        DailyPlanItem item = requireOwnedItem(
                ownerId,
                dailyPlanId,
                dailyPlanVersionId,
                dailyPlanItemId);
        List<DailyPlanTaskStep> steps = taskStepRepository
                .findByDailyPlanItemIdOrderByOrderIndex(item.getId());
        List<TaskStepSnapshot> stepSnapshots = steps.stream()
                .map(this::toSnapshot)
                .toList();

        RoadmapContext roadmapContext = resolveRoadmapContext(
                ownerId,
                plan,
                item);
        List<SourceContext> sources = resolveSources(
                ownerId,
                roadmapContext);
        String locale = userProfileRepository.findByUserId(ownerId)
                .map(UserProfile::getLocale)
                .filter(value -> !value.isBlank())
                .orElse(UserProfile.DEFAULT_LOCALE);

        return new TaskGuidanceContext(
                ownerId,
                dailyPlanId,
                dailyPlanVersionId,
                dailyPlanItemId,
                item.getVersion(),
                locale,
                item.getCategory().name(),
                item.getStatus().name(),
                item.getTitle(),
                item.getDescription(),
                item.getPlannedMinutes(),
                stepSnapshots,
                roadmapContext,
                sources,
                fingerprint(item, stepSnapshots));
    }

    @Transactional(readOnly = true)
    public DailyPlanItem requireOwnedItem(
            UUID ownerId,
            UUID dailyPlanId,
            UUID dailyPlanVersionId,
            UUID dailyPlanItemId) {
        return dailyPlanItemRepository.findOwnedByPath(
                        ownerId,
                        dailyPlanId,
                        dailyPlanVersionId,
                        dailyPlanItemId)
                .orElseThrow(this::itemNotFound);
    }

    private TaskStepSnapshot toSnapshot(DailyPlanTaskStep step) {
        return new TaskStepSnapshot(
                step.getId(),
                step.getVersion(),
                step.getOrderIndex(),
                step.getTitle(),
                step.getGuidance(),
                step.getEstimatedMinutes(),
                Boolean.TRUE.equals(step.getRequired()));
    }

    private RoadmapContext resolveRoadmapContext(
            UUID ownerId,
            DailyPlan plan,
            DailyPlanItem item) {
        if (plan.getRoadmapId() == null || item.getRoadmapItemId() == null) {
            return null;
        }
        RoadmapItem learningUnit = roadmapItemRepository
                .findOwnedByIdWithHierarchy(item.getRoadmapItemId(), ownerId)
                .orElse(null);
        if (learningUnit == null
                || learningUnit.getItemType() != RoadmapItemType.LEARNING_UNIT
                || !plan.getRoadmapId().equals(
                        learningUnit.getRoadmapVersion().getRoadmap().getId())) {
            return null;
        }

        RoadmapItem topic = learningUnit.getParent();
        RoadmapItem milestone = topic == null ? null : topic.getParent();
        return new RoadmapContext(
                plan.getRoadmapId(),
                learningUnit.getId(),
                learningUnit.getTitle(),
                learningUnit.getDescription(),
                topic == null ? null : topic.getId(),
                topic == null ? null : topic.getTitle(),
                milestone == null ? null : milestone.getId(),
                milestone == null ? null : milestone.getTitle());
    }

    private List<SourceContext> resolveSources(
            UUID ownerId,
            RoadmapContext roadmapContext) {
        if (roadmapContext == null) {
            return List.of();
        }

        List<SourceContext> result = new ArrayList<>();
        int remainingCharacters = MAX_SOURCE_CHARACTERS;
        for (RoadmapSource link : roadmapSourceRepository.findOwnedByRoadmapId(
                roadmapContext.roadmapId(), ownerId)) {
            if (result.size() >= MAX_SOURCE_COUNT || remainingCharacters <= 0) {
                break;
            }
            SourceContext source = toSourceContext(link, remainingCharacters);
            if (source == null) {
                continue;
            }
            result.add(source);
            remainingCharacters -= source.boundedContent().length();
        }
        return List.copyOf(result);
    }

    private SourceContext toSourceContext(
            RoadmapSource link,
            int remainingCharacters) {
        Material material = link.getMaterial();
        if (material != null
                && material.getStatus() == MaterialStatus.READY
                && !material.isArchived()
                && hasText(material.getContent())) {
            String label = hasText(material.getOriginalFileName())
                    ? material.getOriginalFileName()
                    : "Personal learning material";
            return new SourceContext(
                    GuidanceReferenceProvenance.MATERIAL,
                    material.getId(),
                    label,
                    bounded(material.getContent(), remainingCharacters));
        }

        LearningSource learningSource = link.getLearningSource();
        if (learningSource != null
                && learningSource.getStatus() == LearningSourceStatus.READY
                && hasText(learningSource.getContentText())) {
            return new SourceContext(
                    GuidanceReferenceProvenance.LEARNING_SOURCE,
                    learningSource.getId(),
                    learningSource.getSourceType().name(),
                    bounded(learningSource.getContentText(), remainingCharacters));
        }
        return null;
    }

    private String bounded(String content, int remainingCharacters) {
        String normalized = content.strip();
        int maximum = Math.min(MAX_CHARACTERS_PER_SOURCE, remainingCharacters);
        return normalized.length() <= maximum
                ? normalized
                : normalized.substring(0, maximum);
    }

    private String fingerprint(
            DailyPlanItem item,
            List<TaskStepSnapshot> steps) {
        StringBuilder canonical = new StringBuilder()
                .append(item.getId())
                .append(':')
                .append(item.getVersion());
        for (TaskStepSnapshot step : steps) {
            canonical.append('|')
                    .append(step.id())
                    .append(':')
                    .append(step.entityVersion())
                    .append(':')
                    .append(step.orderIndex());
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException itemNotFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "Daily Plan item was not found.");
    }
}

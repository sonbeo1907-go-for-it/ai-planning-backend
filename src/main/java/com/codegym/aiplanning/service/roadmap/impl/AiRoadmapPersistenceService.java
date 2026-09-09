package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import com.codegym.aiplanning.entity.source.LearningSource;
import com.codegym.aiplanning.entity.source.LearningSourceStatus;
import com.codegym.aiplanning.repository.MaterialRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.RoadmapGenerationContext;
import com.codegym.aiplanning.service.roadmap.RoadmapGenerationContext.SourceDocument;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedMilestone;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedTopic;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedLearningUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRoadmapPersistenceService {

    private static final int MAX_SOURCE_COUNT = 10;
    private static final int MAX_SOURCE_CHARACTERS = 6000;
    private static final int MAX_TOTAL_SOURCE_CHARACTERS = 24000;

    private final RoadmapRepository roadmapRepository;
    private final RoadmapVersionRepository roadmapVersionRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;
    private final MaterialRepository materialRepository;
    private final AuditLogService auditLogService;

    public AiRoadmapPersistenceService(
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapSourceRepository roadmapSourceRepository,
            MaterialRepository materialRepository,
            AuditLogService auditLogService) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
        this.materialRepository = materialRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RoadmapGenerationContext prepare(
            UUID userId, UUID roadmapId, List<UUID> selectedMaterialIds) {
        Roadmap roadmap = requireOwnedRoadmapForUpdate(userId, roadmapId);
        if (roadmap.getStatus() == RoadmapStatus.ONBOARDING) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_ONBOARDING_INCOMPLETE,
                    "Roadmap onboarding must be completed before AI generation.");
        }
        if (roadmap.getStatus() == RoadmapStatus.ARCHIVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "An archived Roadmap cannot generate a new version.");
        }
        requireNotActivated(roadmap);

        List<RoadmapSource> existingLinks = roadmapSourceRepository.findByRoadmapId(roadmapId);
        attachSelectedMaterials(userId, roadmap, existingLinks, selectedMaterialIds);
        List<SourceDocument> sources = sourceDocuments(
                roadmapSourceRepository.findByRoadmapId(roadmapId));
        if ((roadmap.getTitle() == null || roadmap.getTitle().isBlank()) && sources.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "A learning goal or at least one ready learning material is required.");
        }

        return new RoadmapGenerationContext(
                roadmap.getId(),
                roadmap.getTitle(),
                roadmap.getProficiencyLevel(),
                roadmap.getDailyCommitmentMinutes(),
                roadmap.getExpectedDurationDays(),
                List.copyOf(sources));
    }

    @Transactional
    public RoadmapVersionResponse saveGeneratedVersion(
            UUID userId,
            UUID roadmapId,
            GeneratedRoadmapPlan plan,
            RoadmapVersionOrigin origin) {
        Roadmap roadmap = requireOwnedRoadmapForUpdate(userId, roadmapId);
        requireNotActivated(roadmap);
        supersedeExistingDraft(roadmapId);

        int nextVersionNumber = roadmapVersionRepository
                .findFirstByRoadmapIdOrderByVersionNumberDesc(roadmapId)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);
        RoadmapVersion version = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(roadmap, nextVersionNumber, origin));
        persistItems(version, plan);

        AuditEventAction action = origin == RoadmapVersionOrigin.AI_REGENERATED
                ? AuditEventAction.ROADMAP_VERSION_REGENERATED_BY_AI
                : AuditEventAction.ROADMAP_VERSION_GENERATED_BY_AI;
        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                action,
                "RoadmapVersion",
                version.getId().toString());
        return versionResponse(version);
    }

    private void attachSelectedMaterials(
            UUID userId,
            Roadmap roadmap,
            List<RoadmapSource> existingLinks,
            List<UUID> selectedMaterialIds) {
        List<UUID> distinctIds = selectedMaterialIds == null
                ? List.of()
                : selectedMaterialIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        if (distinctIds.size() > MAX_SOURCE_COUNT) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "At most 10 learning materials may be selected.");
        }

        List<Material> materials = materialRepository
                .findAllByIdInAndUserIdAndArchivedAtIsNull(distinctIds, userId);
        if (materials.size() != distinctIds.size()) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "One or more learning materials were not found.");
        }
        if (materials.stream().anyMatch(material -> material.getStatus() != MaterialStatus.READY
                || material.getContent() == null
                || material.getContent().isBlank())) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only ready learning materials with extracted content may be used.");
        }

        Set<UUID> linkedMaterialIds = new HashSet<>();
        for (RoadmapSource link : existingLinks) {
            if (link.getMaterial() != null) {
                linkedMaterialIds.add(link.getMaterial().getId());
            }
        }
        List<RoadmapSource> newLinks = materials.stream()
                .filter(material -> !linkedMaterialIds.contains(material.getId()))
                .map(material -> RoadmapSource.link(roadmap, material))
                .toList();
        if (!newLinks.isEmpty()) {
            roadmapSourceRepository.saveAllAndFlush(newLinks);
        }
    }

    private List<SourceDocument> sourceDocuments(List<RoadmapSource> links) {
        List<SourceDocument> documents = new ArrayList<>();
        int remainingCharacters = MAX_TOTAL_SOURCE_CHARACTERS;
        for (RoadmapSource link : links) {
            LearningSource learningSource = link.getLearningSource();
            if (learningSource != null
                    && learningSource.getStatus() == LearningSourceStatus.READY
                    && learningSource.getContentText() != null
                    && !learningSource.getContentText().isBlank()) {
                String content = limitedContent(
                        learningSource.getContentText(), remainingCharacters);
                documents.add(new SourceDocument(
                        learningSource.getId(),
                        learningSource.getSourceType().name(),
                        content));
                remainingCharacters -= content.length();
            }

            Material material = link.getMaterial();
            if (remainingCharacters > 0
                    && material != null
                    && !material.isArchived()
                    && material.getStatus() == MaterialStatus.READY
                    && material.getContent() != null
                    && !material.getContent().isBlank()) {
                String content = limitedContent(material.getContent(), remainingCharacters);
                documents.add(new SourceDocument(
                        material.getId(),
                        material.getType().name(),
                        content));
                remainingCharacters -= content.length();
            }
            if (documents.size() == MAX_SOURCE_COUNT || remainingCharacters == 0) {
                break;
            }
        }
        return documents;
    }

    private String limitedContent(String content, int remainingCharacters) {
        String normalized = content.trim();
        int maximumLength = Math.min(MAX_SOURCE_CHARACTERS, remainingCharacters);
        if (normalized.length() <= maximumLength) {
            return normalized;
        }
        return normalized.substring(0, maximumLength);
    }

    private void supersedeExistingDraft(UUID roadmapId) {
        roadmapVersionRepository
                .findByRoadmapIdAndStatus(roadmapId, RoadmapVersionStatus.DRAFT)
                .ifPresent(existingDraft -> {
                    existingDraft.supersedeDraft();
                    roadmapVersionRepository.saveAndFlush(existingDraft);
                });
    }

    private void requireNotActivated(Roadmap roadmap) {
        if (roadmap.getStatus() == RoadmapStatus.ACTIVE
                || roadmap.getActiveVersionId() != null) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_ALREADY_ACTIVATED,
                    "This Roadmap has already been activated. "
                            + "Create an editable copy as a new Roadmap instead.");
        }
    }

    private void persistItems(RoadmapVersion version, GeneratedRoadmapPlan plan) {
        for (GeneratedMilestone generatedMilestone : plan.milestones()) {
            RoadmapItem milestone = roadmapItemRepository.save(RoadmapItem.milestone(
                    version,
                    generatedMilestone.title(),
                    generatedMilestone.description(),
                    generatedMilestone.orderIndex()));
            for (GeneratedTopic generatedTopic : generatedMilestone.topics()) {
                RoadmapItem topic = roadmapItemRepository.saveAndFlush(RoadmapItem.topic(
                        version,
                        milestone,
                        generatedTopic.title(),
                        generatedTopic.description(),
                        generatedTopic.orderIndex(),
                        generatedTopic.estimatedMinutes()));
                for (GeneratedLearningUnit generatedUnit : generatedTopic.learningUnits()) {
                    roadmapItemRepository.save(RoadmapItem.learningUnit(
                            version,
                            topic,
                            generatedUnit.title(),
                            generatedUnit.description(),
                            generatedUnit.orderIndex(),
                            generatedUnit.estimatedMinutes()));
                }
            }
        }
        roadmapItemRepository.flush();
    }

    private RoadmapVersionResponse versionResponse(RoadmapVersion version) {
        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIds(List.of(version.getId()));
        Map<UUID, List<RoadmapItem>> topicsByMilestoneId = new HashMap<>();
        Map<UUID, List<RoadmapItem>> unitsByTopicId = new HashMap<>();
        for (RoadmapItem item : items) {
            if (item.getItemType() == RoadmapItemType.TOPIC && item.getParent() != null) {
                topicsByMilestoneId
                        .computeIfAbsent(item.getParent().getId(), ignored -> new ArrayList<>())
                        .add(item);
            } else if (item.getItemType() == RoadmapItemType.LEARNING_UNIT
                    && item.getParent() != null) {
                unitsByTopicId
                        .computeIfAbsent(item.getParent().getId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        List<RoadmapItemResponse> milestones = items.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.MILESTONE)
                .map(milestone -> RoadmapItemResponse.from(
                        milestone,
                        topicsByMilestoneId
                                .getOrDefault(milestone.getId(), List.of())
                                .stream()
                                .map(topic -> RoadmapItemResponse.from(
                                        topic,
                                        List.of(),
                                        unitsByTopicId
                                                .getOrDefault(topic.getId(), List.of())
                                                .stream()
                                                .map(unit -> RoadmapItemResponse.from(
                                                        unit, List.of(), List.of()))
                                                .toList()))
                                .toList()))
                .toList();
        return RoadmapVersionResponse.from(version, milestones);
    }

    private Roadmap requireOwnedRoadmapForUpdate(UUID userId, UUID roadmapId) {
        return roadmapRepository
                .findOwnedByIdForUpdate(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Roadmap resource was not found."));
    }
}

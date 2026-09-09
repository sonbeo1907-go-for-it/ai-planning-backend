package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.CreateLearningUnitRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateMilestoneRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateTopicRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapSummaryResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapItemRequest;
import com.codegym.aiplanning.controller.roadmap.dto.UpdateRoadmapRequest;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.ManualRoadmapService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class ManualRoadmapServiceImpl implements ManualRoadmapService {

    private final UserAccountRepository userAccountRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapVersionRepository roadmapVersionRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;
    private final AuditLogService auditLogService;

    public ManualRoadmapServiceImpl(
            UserAccountRepository userAccountRepository,
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapSourceRepository roadmapSourceRepository,
            AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public RoadmapResponse create(UUID userId, CreateRoadmapRequest request) {
        UserAccount user = requireUserForUpdate(userId);
        Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.manualDraft(
                user, normalizeRequired(request.title()), normalizeOptional(request.description())));
        RoadmapVersion version = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL));
        auditLogService.logAction(
                userId,
                user.getEmail(),
                AuditEventAction.ROADMAP_CREATED,
                "Roadmap",
                roadmap.getId().toString());
        return RoadmapResponse.from(
                roadmap, List.of(RoadmapVersionResponse.from(version, List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RoadmapSummaryResponse> list(
            UUID userId,
            String query,
            RoadmapStatus status,
            Pageable pageable) {
        String normalizedQuery = normalizeSearchQuery(query);
        Page<Roadmap> roadmaps = findOwnedRoadmaps(
                userId, normalizedQuery, status, pageable);
        if (roadmaps.isEmpty()) {
            return Page.empty(pageable);
        }

        List<RoadmapVersion> versions = roadmapVersionRepository.findAllByRoadmapIds(
                roadmaps.getContent().stream().map(Roadmap::getId).toList());
        Map<UUID, List<RoadmapVersion>> versionsByRoadmapId = new HashMap<>();
        for (RoadmapVersion version : versions) {
            versionsByRoadmapId
                    .computeIfAbsent(version.getRoadmap().getId(), ignored -> new ArrayList<>())
                    .add(version);
        }

        return roadmaps.map(roadmap -> RoadmapSummaryResponse.from(
                roadmap,
                versionsByRoadmapId.getOrDefault(roadmap.getId(), List.of())));
    }

    private Page<Roadmap> findOwnedRoadmaps(
            UUID userId,
            String query,
            RoadmapStatus status,
            Pageable pageable) {
        if (query == null) {
            return status == null
                    ? roadmapRepository.findByOwnerId(userId, pageable)
                    : roadmapRepository.findByOwnerIdAndStatus(userId, status, pageable);
        }

        return status == null
                ? roadmapRepository.searchOwnedByQuery(userId, query, pageable)
                : roadmapRepository.searchOwnedByQueryAndStatus(userId, query, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapResponse get(UUID userId, UUID roadmapId) {
        return roadmapResponse(requireOwned(userId, roadmapId));
    }

    @Override
    @Transactional
    public RoadmapResponse createEditableCopy(UUID userId, UUID roadmapId) {
        Roadmap source = requireOwnedForUpdate(userId, roadmapId);
        if (source.getStatus() != RoadmapStatus.ACTIVE
                || source.getActiveVersionId() == null) {
            throw invalidTransition(
                    "Only an activated Roadmap can be copied as an editable Roadmap.");
        }

        Roadmap copy = roadmapRepository.saveAndFlush(Roadmap.editableCopyOf(
                source,
                copyTitle(source.getTitle())));
        RoadmapVersion copyVersion = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(copy, 1, RoadmapVersionOrigin.MANUAL));
        RoadmapVersion activeSourceVersion = roadmapVersionRepository
                .findByIdAndRoadmapId(source.getActiveVersionId(), source.getId())
                .orElseThrow(this::roadmapDataIntegrityError);

        cloneItems(activeSourceVersion, copyVersion);
        copySourceLinks(source, copy);
        auditLogService.logAction(
                userId,
                source.getOwner().getEmail(),
                AuditEventAction.ROADMAP_COPIED,
                "Roadmap",
                copy.getId().toString());
        return roadmapResponse(copy);
    }

    @Override
    @Transactional
    public RoadmapResponse update(
            UUID userId, UUID roadmapId, UpdateRoadmapRequest request) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        requireNotActivated(roadmap);
        if (roadmap.getVersion() != request.entityVersion()) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "Roadmap was changed by another request. Reload it before saving again.");
        }
        try {
            roadmap.updateMetadata(
                    normalizeRequired(request.title()),
                    normalizeOptional(request.description()));
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        Roadmap saved = roadmapRepository.saveAndFlush(roadmap);
        auditLogService.logAction(
                userId,
                saved.getOwner().getEmail(),
                AuditEventAction.ROADMAP_UPDATED,
                "Roadmap",
                saved.getId().toString());
        return roadmapResponse(saved);
    }

    @Override
    @Transactional
    public RoadmapVersionResponse createDraftVersion(UUID userId, UUID roadmapId) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        if (roadmap.getStatus() == RoadmapStatus.ONBOARDING
                || roadmap.getStatus() == RoadmapStatus.ARCHIVED) {
            throw invalidTransition("Roadmap cannot create a content version in its current state.");
        }
        requireNotActivated(roadmap);
        if (roadmapVersionRepository
                .findByRoadmapIdAndStatus(roadmapId, RoadmapVersionStatus.DRAFT)
                .isPresent()) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_DRAFT_EXISTS,
                    "This Roadmap already has an editable draft version.");
        }

        int nextNumber = roadmapVersionRepository
                        .findFirstByRoadmapIdOrderByVersionNumberDesc(roadmapId)
                        .map(existing -> existing.getVersionNumber() + 1)
                        .orElse(1);
        RoadmapVersion draft = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(
                        roadmap,
                        nextNumber,
                        RoadmapVersionOrigin.MANUAL));
        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_VERSION_CREATED,
                "RoadmapVersion",
                draft.getId().toString());
        return versionResponse(draft);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapVersionResponse getVersion(
            UUID userId, UUID roadmapId, UUID versionId) {
        requireOwned(userId, roadmapId);
        return versionResponse(requireVersion(roadmapId, versionId));
    }

    @Override
    @Transactional
    public RoadmapItemResponse addMilestone(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            CreateMilestoneRequest request) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        List<RoadmapItem> siblings = milestones(versionId);
        int position = insertionPosition(request.orderIndex(), siblings.size());
        shiftForInsertion(siblings, position);
        RoadmapItem milestone = roadmapItemRepository.saveAndFlush(RoadmapItem.milestone(
                editable.version(),
                normalizeRequired(request.title()),
                normalizeOptional(request.description()),
                position));
        auditItem(userId, editable.roadmap(), AuditEventAction.ROADMAP_ITEM_CREATED, milestone);
        return RoadmapItemResponse.from(milestone, List.of());
    }

    @Override
    @Transactional
    public RoadmapItemResponse addTopic(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID milestoneId,
            CreateTopicRequest request) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        RoadmapItem milestone = requireItem(versionId, milestoneId);
        if (milestone.getItemType() != RoadmapItemType.MILESTONE) {
            throw validation("Topics must belong to a Milestone.");
        }
        List<RoadmapItem> siblings = topics(versionId, milestoneId);
        int position = insertionPosition(request.orderIndex(), siblings.size());
        shiftForInsertion(siblings, position);
        RoadmapItem topic = roadmapItemRepository.saveAndFlush(RoadmapItem.topic(
                editable.version(),
                milestone,
                normalizeRequired(request.title()),
                normalizeOptional(request.description()),
                position,
                request.estimatedMinutes()));
        auditItem(userId, editable.roadmap(), AuditEventAction.ROADMAP_ITEM_CREATED, topic);
        return RoadmapItemResponse.from(topic, List.of());
    }

    @Override
    @Transactional
    public RoadmapItemResponse addLearningUnit(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID topicId,
            CreateLearningUnitRequest request) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        RoadmapItem topic = requireItem(versionId, topicId);
        if (topic.getItemType() != RoadmapItemType.TOPIC) {
            throw validation("Learning Units must belong to a Topic.");
        }

        List<RoadmapItem> siblings = learningUnits(versionId, topicId);
        int position = insertionPosition(request.orderIndex(), siblings.size());
        shiftForInsertion(siblings, position);
        RoadmapItem learningUnit = roadmapItemRepository.saveAndFlush(
                RoadmapItem.learningUnit(
                        editable.version(),
                        topic,
                        normalizeRequired(request.title()),
                        normalizeOptional(request.description()),
                        position,
                        request.estimatedMinutes()));
        auditItem(
                userId,
                editable.roadmap(),
                AuditEventAction.ROADMAP_ITEM_CREATED,
                learningUnit);
        return RoadmapItemResponse.from(learningUnit, List.of(), List.of());
    }

    @Override
    @Transactional
    public RoadmapItemResponse updateItem(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID itemId,
            UpdateRoadmapItemRequest request) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        RoadmapItem item = requireItem(versionId, itemId);
        if ((item.getItemType() == RoadmapItemType.TOPIC
                        || item.getItemType() == RoadmapItemType.LEARNING_UNIT)
                && request.estimatedMinutes() == null) {
            throw validation("Estimated minutes are required for a Topic or Learning Unit.");
        }
        if (item.getItemType() == RoadmapItemType.MILESTONE
                && request.estimatedMinutes() != null) {
            throw validation("Estimated minutes belong to Topics and Learning Units, not Milestones.");
        }

        List<RoadmapItem> siblings = item.getParent() == null
                ? milestones(versionId)
                : topics(versionId, item.getParent().getId());
        siblings.removeIf(sibling -> sibling.getId().equals(itemId));
        int position = Math.min(request.orderIndex(), siblings.size());
        item.update(
                normalizeRequired(request.title()),
                normalizeOptional(request.description()),
                position,
                request.estimatedMinutes());
        siblings.add(position, item);
        normalizeOrder(siblings);
        RoadmapItem saved = roadmapItemRepository.saveAndFlush(item);
        auditItem(userId, editable.roadmap(), AuditEventAction.ROADMAP_ITEM_UPDATED, saved);
        return itemResponse(saved);
    }

    @Override
    @Transactional
    public void deleteItem(UUID userId, UUID roadmapId, UUID versionId, UUID itemId) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        RoadmapItem item = requireItem(versionId, itemId);
        List<RoadmapItem> remaining = item.getParent() == null
                ? milestones(versionId)
                : topics(versionId, item.getParent().getId());
        remaining.removeIf(sibling -> sibling.getId().equals(itemId));
        if (item.getItemType() == RoadmapItemType.MILESTONE
                || item.getItemType() == RoadmapItemType.TOPIC) {
            roadmapItemRepository.deleteAllByParentId(itemId);
        }
        roadmapItemRepository.delete(item);
        normalizeOrder(remaining);
        auditItem(userId, editable.roadmap(), AuditEventAction.ROADMAP_ITEM_DELETED, item);
    }

    @Override
    @Transactional
    public RoadmapVersionResponse activate(
            UUID userId, UUID roadmapId, UUID versionId) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        RoadmapVersion version = roadmapVersionRepository
                .findByIdAndRoadmapIdForUpdate(versionId, roadmapId)
                .orElseThrow(this::roadmapNotFound);
        if (version.getStatus() == RoadmapVersionStatus.ACTIVE
                && versionId.equals(roadmap.getActiveVersionId())) {
            return versionResponse(version);
        }
        requireNotActivated(roadmap);
        if (!version.isDraft()) {
            throw invalidTransition("Only a draft Roadmap version can be activated.");
        }
        requireCompleteStructure(versionId);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        version.activate(now);
        roadmap.activateVersion(versionId);
        roadmapVersionRepository.save(version);
        roadmapRepository.saveAndFlush(roadmap);
        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_VERSION_ACTIVATED,
                "RoadmapVersion",
                versionId.toString());
        return versionResponse(version);
    }

    private void cloneItems(RoadmapVersion source, RoadmapVersion target) {
        List<RoadmapItem> sourceItems = roadmapItemRepository
                .findAllByRoadmapVersionIds(List.of(source.getId()));
        List<RoadmapItem> sourceMilestones = sourceItems.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.MILESTONE)
                .toList();
        Map<UUID, List<RoadmapItem>> topicsByMilestoneId = childrenByParent(
                sourceItems, RoadmapItemType.TOPIC);
        Map<UUID, List<RoadmapItem>> unitsByTopicId = childrenByParent(
                sourceItems, RoadmapItemType.LEARNING_UNIT);
        Map<UUID, RoadmapItem> milestoneCopies = new HashMap<>();
        for (RoadmapItem milestone : sourceMilestones) {
            RoadmapItem copy = roadmapItemRepository.saveAndFlush(RoadmapItem.milestone(
                    target,
                    milestone.getTitle(),
                    milestone.getDescription(),
                    milestone.getOrderIndex()));
            milestoneCopies.put(milestone.getId(), copy);
        }
        for (RoadmapItem milestone : sourceMilestones) {
            for (RoadmapItem topic : topicsByMilestoneId.getOrDefault(
                    milestone.getId(), List.of())) {
                RoadmapItem topicCopy = roadmapItemRepository.saveAndFlush(RoadmapItem.topic(
                        target,
                        milestoneCopies.get(milestone.getId()),
                        topic.getTitle(),
                        topic.getDescription(),
                        topic.getOrderIndex(),
                        topic.getEstimatedMinutes()));
                for (RoadmapItem learningUnit : unitsByTopicId.getOrDefault(
                        topic.getId(), List.of())) {
                    roadmapItemRepository.save(RoadmapItem.learningUnit(
                            target,
                            topicCopy,
                            learningUnit.getTitle(),
                            learningUnit.getDescription(),
                            learningUnit.getOrderIndex(),
                            learningUnit.getEstimatedMinutes()));
                }
            }
        }
        roadmapItemRepository.flush();
    }

    private void copySourceLinks(Roadmap source, Roadmap copy) {
        List<RoadmapSource> copiedLinks =
                roadmapSourceRepository.findByRoadmapId(source.getId()).stream()
                        .map(sourceLink -> sourceLink.getLearningSource() != null
                                ? RoadmapSource.link(
                                        copy, sourceLink.getLearningSource())
                                : RoadmapSource.link(
                                        copy, sourceLink.getMaterial()))
                        .toList();
        if (!copiedLinks.isEmpty()) {
            roadmapSourceRepository.saveAllAndFlush(copiedLinks);
        }
    }

    private String copyTitle(String sourceTitle) {
        String baseTitle = sourceTitle == null || sourceTitle.isBlank()
                ? "Roadmap"
                : sourceTitle.strip();
        String suffix = " (Copy)";
        int maximumBaseLength = 200 - suffix.length();
        if (baseTitle.length() > maximumBaseLength) {
            baseTitle = baseTitle.substring(0, maximumBaseLength).stripTrailing();
        }
        return baseTitle + suffix;
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

    private void requireCompleteStructure(UUID versionId) {
        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIds(List.of(versionId));
        List<RoadmapItem> milestones = items.stream()
                .filter(item -> item.getItemType() == RoadmapItemType.MILESTONE)
                .toList();
        Map<UUID, List<RoadmapItem>> topicsByMilestoneId = childrenByParent(
                items, RoadmapItemType.TOPIC);
        Map<UUID, List<RoadmapItem>> unitsByTopicId = childrenByParent(
                items, RoadmapItemType.LEARNING_UNIT);
        if (milestones.isEmpty()
                || milestones.stream().anyMatch(
                        milestone -> topicsByMilestoneId
                                .getOrDefault(milestone.getId(), List.of())
                                .isEmpty())) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                    "Activation requires at least one Milestone and every Milestone requires a Topic.");
        }

        boolean topicWithoutLearningUnits = topicsByMilestoneId.values().stream()
                .flatMap(List::stream)
                .anyMatch(topic -> unitsByTopicId
                        .getOrDefault(topic.getId(), List.of())
                        .isEmpty());
        if (topicWithoutLearningUnits) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                    "Every Topic requires at least one Learning Unit before activation.");
        }
    }

    private RoadmapResponse roadmapResponse(Roadmap roadmap) {
        List<RoadmapVersion> roadmapVersions = roadmapVersionRepository
                .findAllByRoadmapIdOrderByVersionNumberDesc(roadmap.getId());
        if (roadmapVersions.isEmpty()) {
            return RoadmapResponse.from(roadmap, List.of());
        }

        List<RoadmapItem> items = roadmapItemRepository.findAllByRoadmapVersionIds(
                roadmapVersions.stream().map(RoadmapVersion::getId).toList());
        Map<UUID, List<RoadmapItem>> itemsByVersionId = new HashMap<>();
        for (RoadmapItem item : items) {
            itemsByVersionId
                    .computeIfAbsent(
                            item.getRoadmapVersion().getId(), ignored -> new ArrayList<>())
                    .add(item);
        }
        return roadmapListResponse(roadmap, roadmapVersions, itemsByVersionId);
    }

    private RoadmapResponse roadmapListResponse(
            Roadmap roadmap,
            List<RoadmapVersion> versions,
            Map<UUID, List<RoadmapItem>> itemsByVersionId) {
        return RoadmapResponse.from(
                roadmap,
                versions.stream()
                        .map(version -> versionListResponse(
                                version,
                                itemsByVersionId.getOrDefault(version.getId(), List.of())))
                        .toList());
    }

    private RoadmapVersionResponse versionListResponse(
            RoadmapVersion version, List<RoadmapItem> items) {
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
                                .map(topic -> topicResponse(
                                        topic,
                                        unitsByTopicId.getOrDefault(topic.getId(), List.of())))
                                .toList()))
                .toList();
        return RoadmapVersionResponse.from(version, milestones);
    }

    private RoadmapVersionResponse versionResponse(RoadmapVersion version) {
        List<RoadmapItem> items = roadmapItemRepository
                .findAllByRoadmapVersionIds(List.of(version.getId()));
        return versionListResponse(version, items);
    }

    private RoadmapItemResponse itemResponse(RoadmapItem item) {
        return RoadmapItemResponse.from(item, List.of(), List.of());
    }

    private RoadmapItemResponse topicResponse(
            RoadmapItem topic, List<RoadmapItem> learningUnits) {
        return RoadmapItemResponse.from(
                topic,
                List.of(),
                learningUnits.stream().map(this::itemResponse).toList());
    }

    private Map<UUID, List<RoadmapItem>> childrenByParent(
            List<RoadmapItem> items, RoadmapItemType itemType) {
        Map<UUID, List<RoadmapItem>> result = new HashMap<>();
        for (RoadmapItem item : items) {
            if (item.getItemType() == itemType && item.getParent() != null) {
                result.computeIfAbsent(
                                item.getParent().getId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }
        return result;
    }

    private EditableVersion requireEditableVersion(
            UUID userId, UUID roadmapId, UUID versionId) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        RoadmapVersion version = roadmapVersionRepository
                .findByIdAndRoadmapIdForUpdate(versionId, roadmapId)
                .orElseThrow(this::roadmapNotFound);
        if (!version.isDraft()) {
            throw invalidTransition(
                    "Activated Roadmap content is immutable. Create a new draft version to edit it.");
        }
        return new EditableVersion(roadmap, version);
    }

    private UserAccount requireUserForUpdate(UUID userId) {
        UserAccount user = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required."));
        if (user.getRole() != UserRole.USER) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "Only USER accounts can manage Roadmaps.");
        }
        return user;
    }

    private Roadmap requireOwned(UUID userId, UUID roadmapId) {
        return roadmapRepository
                .findByIdAndOwnerId(roadmapId, userId)
                .orElseThrow(this::roadmapNotFound);
    }

    private Roadmap requireOwnedForUpdate(UUID userId, UUID roadmapId) {
        return roadmapRepository
                .findOwnedByIdForUpdate(roadmapId, userId)
                .orElseThrow(this::roadmapNotFound);
    }

    private RoadmapVersion requireVersion(UUID roadmapId, UUID versionId) {
        return roadmapVersionRepository
                .findByIdAndRoadmapId(versionId, roadmapId)
                .orElseThrow(this::roadmapNotFound);
    }

    private RoadmapItem requireItem(UUID versionId, UUID itemId) {
        return roadmapItemRepository
                .findByIdAndRoadmapVersionId(itemId, versionId)
                .orElseThrow(this::roadmapNotFound);
    }

    private List<RoadmapItem> milestones(UUID versionId) {
        return new ArrayList<>(roadmapItemRepository
                .findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(
                        versionId, RoadmapItemType.MILESTONE));
    }

    private List<RoadmapItem> topics(UUID versionId, UUID milestoneId) {
        return new ArrayList<>(roadmapItemRepository
                .findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(
                        versionId, milestoneId));
    }

    private List<RoadmapItem> learningUnits(UUID versionId, UUID topicId) {
        return new ArrayList<>(roadmapItemRepository
                .findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(
                        versionId, topicId));
    }

    private int insertionPosition(Integer requested, int size) {
        return requested == null ? size : Math.min(requested, size);
    }

    private void shiftForInsertion(List<RoadmapItem> siblings, int position) {
        for (int index = position; index < siblings.size(); index++) {
            siblings.get(index).moveTo(index + 1);
        }
        roadmapItemRepository.saveAll(siblings);
    }

    private void normalizeOrder(List<RoadmapItem> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            siblings.get(index).moveTo(index);
        }
        roadmapItemRepository.saveAll(siblings);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw validation("Title must not be blank.");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSearchQuery(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void auditItem(
            UUID userId,
            Roadmap roadmap,
            AuditEventAction action,
            RoadmapItem item) {
        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                action,
                "RoadmapItem",
                item.getId().toString());
    }

    private BusinessException roadmapNotFound() {
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "Roadmap resource was not found.");
    }

    private BusinessException roadmapDataIntegrityError() {
        return new BusinessException(
                ErrorCode.INTERNAL_ERROR, "Roadmap version data is inconsistent.");
    }

    private BusinessException invalidTransition(String message) {
        return new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, message);
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record EditableVersion(Roadmap roadmap, RoadmapVersion version) {}
}

package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.CreateMilestoneRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateRoadmapRequest;
import com.codegym.aiplanning.controller.roadmap.dto.CreateTopicRequest;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
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
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.ManualRoadmapService;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
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
    private final AuditLogService auditLogService;
    private final RoadmapProgressService roadmapProgressService;

    public ManualRoadmapServiceImpl(
            UserAccountRepository userAccountRepository,
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository,
            AuditLogService auditLogService,
            RoadmapProgressService roadmapProgressService) {
        this.userAccountRepository = userAccountRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.auditLogService = auditLogService;
        this.roadmapProgressService = roadmapProgressService;
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
        RoadmapProgressResponse progress = roadmapProgressService.calculateRoadmapProgress(
                roadmap.getId(), roadmap.getActiveVersionId(), userId);
        return RoadmapResponse.from(
                roadmap, List.of(RoadmapVersionResponse.from(version, List.of())), progress);
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
        return roadmapResponse(requireOwned(userId, roadmapId), userId);
    }

    @Override
    @Transactional
    public RoadmapResponse update(
            UUID userId, UUID roadmapId, UpdateRoadmapRequest request) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
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
        return roadmapResponse(saved, userId);
    }

    @Override
    @Transactional
    public RoadmapVersionResponse createDraftVersion(UUID userId, UUID roadmapId) {
        Roadmap roadmap = requireOwnedForUpdate(userId, roadmapId);
        if (roadmap.getStatus() == RoadmapStatus.ONBOARDING
                || roadmap.getStatus() == RoadmapStatus.ARCHIVED) {
            throw invalidTransition("Roadmap cannot create a content version in its current state.");
        }
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
        RoadmapVersionOrigin origin = roadmap.getActiveVersionId() == null
                ? RoadmapVersionOrigin.MANUAL
                : RoadmapVersionOrigin.USER_EDITED;
        RoadmapVersion draft = roadmapVersionRepository.saveAndFlush(
                RoadmapVersion.draft(roadmap, nextNumber, origin));
        if (roadmap.getActiveVersionId() != null) {
            RoadmapVersion active = roadmapVersionRepository
                    .findByIdAndRoadmapId(roadmap.getActiveVersionId(), roadmapId)
                    .orElseThrow(this::roadmapDataIntegrityError);
            cloneItems(active, draft);
        }
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
    public RoadmapItemResponse updateItem(
            UUID userId,
            UUID roadmapId,
            UUID versionId,
            UUID itemId,
            UpdateRoadmapItemRequest request) {
        EditableVersion editable = requireEditableVersion(userId, roadmapId, versionId);
        RoadmapItem item = requireItem(versionId, itemId);
        if (item.getItemType() == RoadmapItemType.TOPIC
                && request.estimatedMinutes() == null) {
            throw validation("Estimated minutes are required for a Topic.");
        }
        if (item.getItemType() == RoadmapItemType.MILESTONE
                && request.estimatedMinutes() != null) {
            throw validation("Estimated minutes belong to Topics, not Milestones.");
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
        if (item.getItemType() == RoadmapItemType.MILESTONE) {
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
        if (!version.isDraft()) {
            throw invalidTransition("Only a draft Roadmap version can be activated.");
        }
        requireCompleteStructure(versionId);

        if (roadmap.getActiveVersionId() != null) {
            RoadmapVersion previous = roadmapVersionRepository
                    .findByIdAndRoadmapIdForUpdate(roadmap.getActiveVersionId(), roadmapId)
                    .orElseThrow(this::roadmapDataIntegrityError);
            previous.supersede();
            roadmapVersionRepository.save(previous);
        }
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
        List<RoadmapItem> sourceMilestones = milestones(source.getId());
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
            for (RoadmapItem topic : topics(source.getId(), milestone.getId())) {
                roadmapItemRepository.save(RoadmapItem.topic(
                        target,
                        milestoneCopies.get(milestone.getId()),
                        topic.getTitle(),
                        topic.getDescription(),
                        topic.getOrderIndex(),
                        topic.getEstimatedMinutes()));
            }
        }
        roadmapItemRepository.flush();
    }

    private void requireCompleteStructure(UUID versionId) {
        List<RoadmapItem> milestones = milestones(versionId);
        if (milestones.isEmpty()
                || roadmapItemRepository.countByRoadmapVersionIdAndItemType(
                                versionId, RoadmapItemType.TOPIC)
                        == 0
                || milestones.stream().anyMatch(
                        milestone -> topics(versionId, milestone.getId()).isEmpty())) {
            throw new BusinessException(
                    ErrorCode.ROADMAP_STRUCTURE_INCOMPLETE,
                    "Activation requires at least one Milestone and every Milestone requires a Topic.");
        }
    }

    private RoadmapResponse roadmapResponse(Roadmap roadmap, UUID userId) {
        List<RoadmapVersionResponse> versions = roadmapVersionRepository
                .findAllByRoadmapIdOrderByVersionNumberDesc(roadmap.getId())
                .stream()
                .map(this::versionResponse)
                .toList();
        RoadmapProgressResponse progress = roadmapProgressService.calculateRoadmapProgress(
                roadmap.getId(), roadmap.getActiveVersionId(), userId);
        return RoadmapResponse.from(roadmap, versions, progress);
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
        for (RoadmapItem item : items) {
            if (item.getItemType() == RoadmapItemType.TOPIC && item.getParent() != null) {
                topicsByMilestoneId
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
                                .map(this::itemResponse)
                                .toList()))
                .toList();
        return RoadmapVersionResponse.from(version, milestones);
    }

    private RoadmapVersionResponse versionResponse(RoadmapVersion version) {
        List<RoadmapItemResponse> responses = milestones(version.getId()).stream()
                .map(milestone -> RoadmapItemResponse.from(
                        milestone,
                        topics(version.getId(), milestone.getId()).stream()
                                .map(this::itemResponse)
                                .toList()))
                .toList();
        return RoadmapVersionResponse.from(version, responses);
    }

    private RoadmapItemResponse itemResponse(RoadmapItem item) {
        return RoadmapItemResponse.from(item, List.of());
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

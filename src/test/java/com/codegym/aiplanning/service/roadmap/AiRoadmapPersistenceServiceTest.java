package com.codegym.aiplanning.service.roadmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus;
import com.codegym.aiplanning.repository.MaterialRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.impl.AiRoadmapPersistenceService;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedMilestone;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedTopic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiRoadmapPersistenceServiceTest {

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private RoadmapVersionRepository roadmapVersionRepository;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapSourceRepository roadmapSourceRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private AuditLogService auditLogService;

    private AiRoadmapPersistenceService service;
    private UUID userId;
    private UUID roadmapId;
    private Roadmap roadmap;
    private UserAccount owner;

    @BeforeEach
    void setUp() {
        service = new AiRoadmapPersistenceService(
                roadmapRepository,
                roadmapVersionRepository,
                roadmapItemRepository,
                roadmapSourceRepository,
                materialRepository,
                auditLogService);
        userId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        owner = org.mockito.Mockito.mock(UserAccount.class);
        roadmap = org.mockito.Mockito.mock(Roadmap.class);
    }

    @Test
    void attachesOnlyReadyOwnerMaterialsToGenerationContext() {
        when(owner.getId()).thenReturn(userId);
        when(roadmap.getId()).thenReturn(roadmapId);
        when(roadmap.getOwner()).thenReturn(owner);
        UUID materialId = UUID.randomUUID();
        Material material = org.mockito.Mockito.mock(Material.class);
        when(material.getId()).thenReturn(materialId);
        when(material.getUser()).thenReturn(owner);
        when(material.getStatus()).thenReturn(MaterialStatus.READY);
        when(material.getType()).thenReturn(MaterialType.TEXT);
        when(material.getContent()).thenReturn("Nội dung tài liệu");
        when(material.isArchived()).thenReturn(false);
        RoadmapSource linkedSource = RoadmapSource.link(roadmap, material);

        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(roadmap.getStatus()).thenReturn(RoadmapStatus.DRAFT);
        when(roadmap.getTitle()).thenReturn("Backend Java");
        when(roadmapSourceRepository.findByRoadmapId(roadmapId))
                .thenReturn(List.of(), List.of(linkedSource));
        when(materialRepository.findAllByIdInAndUserIdAndArchivedAtIsNull(
                        List.of(materialId), userId))
                .thenReturn(List.of(material));

        RoadmapGenerationContext context =
                service.prepare(userId, roadmapId, List.of(materialId));

        assertEquals(1, context.sources().size());
        assertEquals(materialId, context.sources().get(0).id());
        assertEquals("Nội dung tài liệu", context.sources().get(0).content());
        verify(roadmapSourceRepository).saveAllAndFlush(anyList());
    }

    @Test
    void hidesWhetherAnUnownedSelectedMaterialExists() {
        UUID materialId = UUID.randomUUID();
        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(roadmap.getStatus()).thenReturn(RoadmapStatus.DRAFT);
        when(roadmapSourceRepository.findByRoadmapId(roadmapId)).thenReturn(List.of());
        when(materialRepository.findAllByIdInAndUserIdAndArchivedAtIsNull(
                        List.of(materialId), userId))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.prepare(userId, roadmapId, List.of(materialId)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode());
        verify(roadmapSourceRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void regenerationSupersedesTheDraftWithoutDeletingItsHistory() {
        when(owner.getEmail()).thenReturn("user@example.com");
        when(roadmap.getId()).thenReturn(roadmapId);
        when(roadmap.getOwner()).thenReturn(owner);
        RoadmapVersion existingDraft =
                RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(existingDraft, "id", UUID.randomUUID());

        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId))
                .thenReturn(Optional.of(roadmap));
        when(roadmapVersionRepository.findByRoadmapIdAndStatus(
                        roadmapId, RoadmapVersionStatus.DRAFT))
                .thenReturn(Optional.of(existingDraft));
        when(roadmapVersionRepository.findFirstByRoadmapIdOrderByVersionNumberDesc(roadmapId))
                .thenReturn(Optional.of(existingDraft));
        when(roadmapVersionRepository.saveAndFlush(any(RoadmapVersion.class)))
                .thenAnswer(invocation -> {
                    RoadmapVersion version = invocation.getArgument(0, RoadmapVersion.class);
                    if (version.getId() == null) {
                        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
                    }
                    return version;
                });
        when(roadmapItemRepository.save(any(RoadmapItem.class)))
                .thenAnswer(invocation -> {
                    RoadmapItem item = invocation.getArgument(0, RoadmapItem.class);
                    if (item.getId() == null) {
                        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                    }
                    return item;
                });
        when(roadmapItemRepository.findAllByRoadmapVersionIds(anyList()))
                .thenReturn(List.of());

        RoadmapVersionResponse response = service.saveGeneratedVersion(
                userId,
                roadmapId,
                validPlan(),
                RoadmapVersionOrigin.AI_REGENERATED);

        assertEquals(RoadmapVersionStatus.SUPERSEDED, existingDraft.getStatus());
        assertEquals(2, response.versionNumber());
        assertFalse(response.milestones().iterator().hasNext());
        verify(roadmapVersionRepository, never()).delete(any(RoadmapVersion.class));
        verify(auditLogService).logAction(
                eq(userId),
                eq("user@example.com"),
                eq(com.codegym.aiplanning.entity.audit.AuditEventAction
                        .ROADMAP_VERSION_REGENERATED_BY_AI),
                eq("RoadmapVersion"),
                any());
    }

    private GeneratedRoadmapPlan validPlan() {
        List<GeneratedTopic> topics = List.of(
                new GeneratedTopic("Chủ đề 1", "Mô tả", 0, 60),
                new GeneratedTopic("Chủ đề 2", "Mô tả", 1, 60));
        return new GeneratedRoadmapPlan(
                "Lộ trình",
                "Mô tả",
                List.of(
                        new GeneratedMilestone("Cột mốc 1", "Mô tả", 0, topics),
                        new GeneratedMilestone("Cột mốc 2", "Mô tả", 1, topics),
                        new GeneratedMilestone("Cột mốc 3", "Mô tả", 2, topics)));
    }
}

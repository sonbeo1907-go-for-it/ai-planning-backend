package com.codegym.aiplanning.service.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.impl.ManualRoadmapServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ManualRoadmapServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private RoadmapVersionRepository roadmapVersionRepository;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private AuditLogService auditLogService;

    private ManualRoadmapService service;

    @BeforeEach
    void setUp() {
        service = new ManualRoadmapServiceImpl(
                userAccountRepository,
                roadmapRepository,
                roadmapVersionRepository,
                roadmapItemRepository,
                auditLogService);
    }

    @Test
    void listLoadsAllNestedRoadmapContentUsingBulkRepositoryCalls() {
        UUID userId = UUID.randomUUID();
        UserAccount owner = UserAccount.create(
                "owner@example.com", "Password1", UserRole.USER, AccountStatus.ACTIVE);
        setId(owner, userId);

        Roadmap backend = Roadmap.manualDraft(owner, "Backend", null);
        Roadmap frontend = Roadmap.manualDraft(owner, "Frontend", null);
        setId(backend, UUID.randomUUID());
        setId(frontend, UUID.randomUUID());

        RoadmapVersion backendVersion =
                RoadmapVersion.draft(backend, 1, RoadmapVersionOrigin.MANUAL);
        RoadmapVersion frontendVersion =
                RoadmapVersion.draft(frontend, 1, RoadmapVersionOrigin.MANUAL);
        setId(backendVersion, UUID.randomUUID());
        setId(frontendVersion, UUID.randomUUID());

        RoadmapItem backendMilestone =
                RoadmapItem.milestone(backendVersion, "Week 1", null, 0);
        RoadmapItem backendTopic =
                RoadmapItem.topic(backendVersion, backendMilestone, "Spring", null, 0, 60);
        RoadmapItem frontendMilestone =
                RoadmapItem.milestone(frontendVersion, "Week 1", null, 0);
        RoadmapItem frontendTopic =
                RoadmapItem.topic(frontendVersion, frontendMilestone, "React", null, 0, 45);
        setId(backendMilestone, UUID.randomUUID());
        setId(backendTopic, UUID.randomUUID());
        setId(frontendMilestone, UUID.randomUUID());
        setId(frontendTopic, UUID.randomUUID());

        List<Roadmap> roadmaps = List.of(backend, frontend);
        List<RoadmapVersion> versions = List.of(backendVersion, frontendVersion);
        List<RoadmapItem> items = List.of(
                backendMilestone, backendTopic, frontendMilestone, frontendTopic);
        List<UUID> roadmapIds = roadmaps.stream().map(Roadmap::getId).toList();
        List<UUID> versionIds = versions.stream().map(RoadmapVersion::getId).toList();

        when(roadmapRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId))
                .thenReturn(roadmaps);
        when(roadmapVersionRepository.findAllByRoadmapIds(roadmapIds))
                .thenReturn(versions);
        when(roadmapItemRepository.findAllByRoadmapVersionIds(versionIds))
                .thenReturn(items);

        List<RoadmapResponse> response = service.list(userId);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).versions()).hasSize(1);
        assertThat(response.get(0).versions().get(0).milestones()).hasSize(1);
        assertThat(response.get(0).versions().get(0).milestones().get(0).topics())
                .extracting(topic -> topic.title())
                .containsExactly("Spring");
        assertThat(response.get(1).versions().get(0).milestones().get(0).topics())
                .extracting(topic -> topic.title())
                .containsExactly("React");

        verify(roadmapVersionRepository).findAllByRoadmapIds(roadmapIds);
        verify(roadmapItemRepository).findAllByRoadmapVersionIds(versionIds);
        verify(roadmapVersionRepository, never())
                .findAllByRoadmapIdOrderByVersionNumberDesc(
                        org.mockito.ArgumentMatchers.any());
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listDoesNotRunBulkChildQueriesWhenUserHasNoRoadmaps() {
        UUID userId = UUID.randomUUID();
        when(roadmapRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of());

        assertThat(service.list(userId)).isEmpty();

        verify(roadmapVersionRepository, never())
                .findAllByRoadmapIds(org.mockito.ArgumentMatchers.any());
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIds(org.mockito.ArgumentMatchers.any());
    }

    private void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}

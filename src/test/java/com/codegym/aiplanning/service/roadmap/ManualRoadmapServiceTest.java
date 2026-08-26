package com.codegym.aiplanning.service.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.roadmap.dto.RoadmapSummaryResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
    void listReturnsLightweightSummariesUsingBulkVersionLookup() {
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

        List<Roadmap> roadmaps = List.of(backend, frontend);
        List<RoadmapVersion> versions = List.of(backendVersion, frontendVersion);
        List<UUID> roadmapIds = roadmaps.stream().map(Roadmap::getId).toList();
        PageRequest pageable = PageRequest.of(0, 20);

        when(roadmapRepository.searchOwned(userId, null, null, pageable))
                .thenReturn(new PageImpl<>(roadmaps, pageable, roadmaps.size()));
        when(roadmapVersionRepository.findAllByRoadmapIds(roadmapIds))
                .thenReturn(versions);

        Page<RoadmapSummaryResponse> response =
                service.list(userId, null, null, pageable);

        assertThat(response).hasSize(2);
        assertThat(response.getContent())
                .extracting(RoadmapSummaryResponse::title)
                .containsExactly("Backend", "Frontend");
        assertThat(response.getContent())
                .extracting(RoadmapSummaryResponse::versionCount)
                .containsExactly(1, 1);

        verify(roadmapVersionRepository).findAllByRoadmapIds(roadmapIds);
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIds(org.mockito.ArgumentMatchers.any());
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
        PageRequest pageable = PageRequest.of(0, 20);
        when(roadmapRepository.searchOwned(userId, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        assertThat(service.list(userId, null, null, pageable)).isEmpty();

        verify(roadmapVersionRepository, never())
                .findAllByRoadmapIds(org.mockito.ArgumentMatchers.any());
        verify(roadmapItemRepository, never())
                .findAllByRoadmapVersionIds(org.mockito.ArgumentMatchers.any());
    }

    private void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}

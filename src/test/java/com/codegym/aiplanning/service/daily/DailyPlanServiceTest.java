package com.codegym.aiplanning.service.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanSummaryResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.impl.DailyPlanServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanVersionResponse;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiResponse;
import com.codegym.aiplanning.service.daily.ai.DailyPlanAiGenerator;
import com.codegym.aiplanning.service.daily.ai.DailyPlanningContext;
import com.codegym.aiplanning.service.daily.ai.PlanningContextBuilder;
import com.codegym.aiplanning.service.daily.DailyPlanPersistenceService;
import com.codegym.aiplanning.entity.daily.DailyPlanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DailyPlanServiceTest {

    @Mock
    private DailyPlanRepository dailyPlanRepository;
    @Mock
    private DailyPlanVersionRepository dailyPlanVersionRepository;
    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;
    @Mock
    private ProgressEntryRepository progressEntryRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private RoadmapRepository roadmapRepository;
    @Mock
    private RoadmapItemRepository roadmapItemRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlanningContextBuilder contextBuilder;
    @Mock
    private DailyPlanAiGenerator aiGenerator;
    @Mock
    private DailyPlanPersistenceService persistenceService;

    private DailyPlanServiceImpl dailyPlanService;

    private UUID userId;
    private Jwt userJwt;

    @BeforeEach
    void setUp() {
        dailyPlanService = new DailyPlanServiceImpl(
                dailyPlanRepository,
                dailyPlanVersionRepository,
                dailyPlanItemRepository,
                progressEntryRepository,
                userProfileRepository,
                roadmapRepository,
                roadmapItemRepository,
                auditLogService,
                contextBuilder,
                aiGenerator,
                persistenceService);

        userId = UUID.randomUUID();
        userJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", userId.toString())
                .claim("preferred_username", "testuser")
                .build();
    }

    @Test
    void createDailyPlan_success() {
        LocalDate date = LocalDate.now();
        CreateDailyPlanRequest request = new CreateDailyPlanRequest(date, 120, null);

        when(dailyPlanRepository.findByUserIdAndPlanDate(userId, date)).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        when(dailyPlanRepository.save(any(DailyPlan.class))).thenAnswer(inv -> {
            DailyPlan plan = inv.getArgument(0);
            if (plan.getId() == null) {
                ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
            }
            return plan;
        });

        when(dailyPlanVersionRepository.save(any(DailyPlanVersion.class))).thenAnswer(inv -> {
            DailyPlanVersion version = inv.getArgument(0);
            if (version.getId() == null) {
                ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
            }
            return version;
        });

        DailyPlanResponse response = dailyPlanService.createDailyPlan(request, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.planDate()).isEqualTo(date);
        assertThat(response.availableMinutes()).isEqualTo(120);
        assertThat(response.completionPercentage()).isEqualTo(0.0);
    }

    @Test
    void createDailyPlan_duplicateDate_throwsException() {
        LocalDate date = LocalDate.now();
        CreateDailyPlanRequest request = new CreateDailyPlanRequest(date, 60, null);

        DailyPlan existing = DailyPlan.create(userId, date, "UTC");
        when(dailyPlanRepository.findByUserIdAndPlanDate(userId, date)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> dailyPlanService.createDailyPlan(request, userJwt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getUserDailyPlansLoadsCurrentVersionsAndItemsInBulk() {
        DailyPlan firstPlan = DailyPlan.create(
                userId, LocalDate.now(), "Asia/Ho_Chi_Minh");
        DailyPlan secondPlan = DailyPlan.create(
                userId, LocalDate.now().minusDays(1), "Asia/Ho_Chi_Minh");
        UUID firstPlanId = UUID.randomUUID();
        UUID secondPlanId = UUID.randomUUID();
        UUID firstVersionId = UUID.randomUUID();
        UUID secondVersionId = UUID.randomUUID();
        ReflectionTestUtils.setField(firstPlan, "id", firstPlanId);
        ReflectionTestUtils.setField(secondPlan, "id", secondPlanId);
        firstPlan.updateActiveVersion(firstVersionId);

        DailyPlanVersion firstVersion = DailyPlanVersion.create(
                firstPlanId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
        DailyPlanVersion secondVersion = DailyPlanVersion.create(
                secondPlanId, 2, DailyPlanVersionOrigin.USER_EDITED, 90, 45);
        ReflectionTestUtils.setField(firstVersion, "id", firstVersionId);
        ReflectionTestUtils.setField(secondVersion, "id", secondVersionId);

        DailyPlanItem firstItem = DailyPlanItem.create(
                firstVersionId, DailyTaskCategory.CUSTOM, "Backend task", null, 30, 0);
        DailyPlanItem secondItem = DailyPlanItem.create(
                secondVersionId, DailyTaskCategory.CUSTOM, "Frontend task", null, 45, 0);
        ReflectionTestUtils.setField(firstItem, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(secondItem, "id", UUID.randomUUID());

        List<DailyPlan> plans = List.of(firstPlan, secondPlan);
        List<DailyPlanVersion> versions = List.of(firstVersion, secondVersion);
        List<DailyPlanItem> items = List.of(firstItem, secondItem);
        List<UUID> planIds = plans.stream().map(DailyPlan::getId).toList();
        List<UUID> versionIds = versions.stream().map(DailyPlanVersion::getId).toList();
        PageRequest pageable = PageRequest.of(0, 20);

        when(dailyPlanRepository.searchOwned(
                        userId, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(plans, pageable, plans.size()));
        when(dailyPlanVersionRepository.findCurrentVersionsByDailyPlanIds(planIds))
                .thenReturn(versions);
        when(dailyPlanItemRepository.findByDailyPlanVersionIds(versionIds))
                .thenReturn(items);

        Page<DailyPlanSummaryResponse> response = dailyPlanService.getUserDailyPlans(
                null, null, null, null, pageable, userJwt);

        assertThat(response).hasSize(2);
        assertThat(response.getContent())
                .extracting(DailyPlanSummaryResponse::latestVersionId)
                .containsExactly(firstVersionId, secondVersionId);
        assertThat(response.getContent())
                .extracting(DailyPlanSummaryResponse::totalItemsCount)
                .containsExactly(1, 1);

        verify(dailyPlanVersionRepository).findCurrentVersionsByDailyPlanIds(planIds);
        verify(dailyPlanItemRepository).findByDailyPlanVersionIds(versionIds);
        verify(dailyPlanVersionRepository, never())
                .findTopByDailyPlanIdOrderByVersionNumberDesc(any());
        verify(dailyPlanVersionRepository, never()).findById(any());
        verify(dailyPlanItemRepository, never())
                .findByDailyPlanVersionIdOrderByOrderIndexAsc(any());
    }

    @Test
    void addTaskToPlan_success() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.updateActiveVersion(versionId);

        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(version, "id", versionId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId)).thenReturn(List.of());
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> {
            DailyPlanItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(item, "createdAt", Instant.now());
            return item;
        });

        CreateDailyTaskRequest taskRequest = new CreateDailyTaskRequest("Học Java", "Đọc tài liệu", DailyTaskCategory.CUSTOM, 30, null);
        DailyPlanItemResponse response =
                dailyPlanService.addTaskToPlan(planId, versionId, taskRequest, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Học Java");
        assertThat(response.status()).isEqualTo(DailyTaskStatus.NOT_STARTED);
    }

    @Test
    void recordProgress_completesAndCalculatesPercentage() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
        ReflectionTestUtils.setField(version, "id", versionId);
        version.activate(Instant.now());
        plan.activateVersion(versionId);

        DailyPlanItem item = DailyPlanItem.create(versionId, DailyTaskCategory.CUSTOM, "Task 1", "Desc", 30, 0);
        ReflectionTestUtils.setField(item, "id", itemId);

        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressEntryRepository.save(any(ProgressEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordProgressRequest updateReq = new RecordProgressRequest(com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED, 30, "Done", 3, 4, "Notes");
        DailyPlanItemResponse response = dailyPlanService.recordProgress(planId, itemId, updateReq, userJwt);

        assertThat(response.status()).isEqualTo(DailyTaskStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = ProgressEntryStatus.class,
            names = {"PARTIALLY_COMPLETED", "SKIPPED"})
    void recordProgress_preservesNonCompletedOutcomeInTaskSnapshot(
            ProgressEntryStatus progressStatus) {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        DailyPlanVersion version = DailyPlanVersion.create(
                planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
        ReflectionTestUtils.setField(version, "id", versionId);
        version.activate(Instant.now());
        plan.activateVersion(versionId);

        DailyPlanItem item = DailyPlanItem.create(
                versionId, DailyTaskCategory.CUSTOM, "Task outcome", null, 30, 0);
        ReflectionTestUtils.setField(item, "id", itemId);
        item.updateStatus(DailyTaskStatus.COMPLETED);

        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyPlanItemResponse response = dailyPlanService.recordProgress(
                planId,
                itemId,
                new RecordProgressRequest(progressStatus, 10, null, null, null, null),
                userJwt);

        DailyTaskStatus expectedTaskStatus = DailyTaskStatus.valueOf(progressStatus.name());
        assertThat(response.status()).isEqualTo(expectedTaskStatus);
        assertThat(response.completedAt()).isNull();

        ArgumentCaptor<ProgressEntry> entryCaptor =
                ArgumentCaptor.forClass(ProgressEntry.class);
        verify(progressEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getStatus()).isEqualTo(progressStatus);
        assertThat(entryCaptor.getValue().getCompletionPercentage())
                .isEqualTo(expectedTaskStatus.completionPercentage());
    }

    @Test
    void recordPomodoroSession_success() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(version, "id", versionId);
        version.activate(Instant.now());
        plan.activateVersion(versionId);

        DailyPlanItem item = DailyPlanItem.create(versionId, DailyTaskCategory.CUSTOM, "Pomodoro Task", "Desc", 25, 0);
        ReflectionTestUtils.setField(item, "id", itemId);

        when(dailyPlanRepository.findByIdAndUserIdForUpdate(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressEntryRepository.save(any(ProgressEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordPomodoroSessionRequest pomodoroReq = new RecordPomodoroSessionRequest(25);
        DailyPlanItemResponse response = dailyPlanService.recordPomodoroSession(planId, itemId, pomodoroReq, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(DailyTaskStatus.IN_PROGRESS);
    }

    @Test
    void createDailyPlanWithRoadmapKeepsManualDraftEmpty() {
        LocalDate date = LocalDate.now();
        UUID roadmapId = UUID.randomUUID();
        UUID activeVersionId = UUID.randomUUID();
        
        CreateDailyPlanRequest request = new CreateDailyPlanRequest(date, 60, roadmapId);

        UserAccount owner = UserAccount.create("owner@example.com", "Password@123", UserRole.USER, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", userId);
        
        Roadmap roadmap = Roadmap.manualDraft(owner, "Roadmap Title", "Desc");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        roadmap.activateVersion(activeVersionId);

        when(dailyPlanRepository.findByUserIdAndPlanDate(userId, date)).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId)).thenReturn(Optional.of(roadmap));

        when(dailyPlanRepository.save(any(DailyPlan.class))).thenAnswer(inv -> {
            DailyPlan plan = inv.getArgument(0);
            ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
            return plan;
        });
        when(dailyPlanVersionRepository.save(any(DailyPlanVersion.class))).thenAnswer(inv -> {
            DailyPlanVersion dpv = inv.getArgument(0);
            ReflectionTestUtils.setField(dpv, "id", UUID.randomUUID());
            return dpv;
        });

        DailyPlanResponse response = dailyPlanService.createDailyPlan(request, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.roadmapId()).isEqualTo(roadmapId);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalPlannedMinutes()).isZero();
        verify(dailyPlanItemRepository, never()).save(any());
    }

    @Test
    void addTaskToPlan_withRoadmapItemId_success() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID roadmapId = UUID.randomUUID();
        UUID activeVersionId = UUID.randomUUID();
        UUID roadmapItemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.updateActiveVersion(versionId);

        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(version, "id", versionId);

        UserAccount owner = UserAccount.create("owner@example.com", "Password@123", UserRole.USER, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", userId);

        Roadmap roadmap = Roadmap.manualDraft(owner, "Roadmap Title", "Desc");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);

        RoadmapVersion rVersion = RoadmapVersion.draft(roadmap, 1, com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(rVersion, "id", activeVersionId);

        RoadmapItem topic = RoadmapItem.topic(rVersion, null, "Roadmap Topic", "Desc", 0, 30);
        ReflectionTestUtils.setField(topic, "id", roadmapItemId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId)).thenReturn(List.of());
        when(roadmapItemRepository.findById(roadmapItemId)).thenReturn(Optional.of(topic));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> {
            DailyPlanItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(item, "createdAt", Instant.now());
            return item;
        });

        CreateDailyTaskRequest taskRequest = new CreateDailyTaskRequest("Học Java", "Đọc tài liệu", DailyTaskCategory.CUSTOM, 30, roadmapItemId);
        DailyPlanItemResponse response =
                dailyPlanService.addTaskToPlan(planId, versionId, taskRequest, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.roadmapItemId()).isEqualTo(roadmapItemId);
        assertThat(plan.getRoadmapId()).isEqualTo(roadmapId);
    }

    @Test
    void deleteTask_withProgressHistory_isRejected() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        DailyPlanVersion version = DailyPlanVersion.create(
                planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
        ReflectionTestUtils.setField(version, "id", versionId);
        DailyPlanItem item = DailyPlanItem.create(
                versionId, DailyTaskCategory.CUSTOM, "Protected task", null, 30, 0);
        ReflectionTestUtils.setField(item, "id", itemId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(progressEntryRepository.existsByDailyPlanItemId(itemId)).thenReturn(true);

        assertThatThrownBy(() ->
                        dailyPlanService.deleteTask(planId, versionId, itemId, userJwt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("progress history");

        verify(dailyPlanItemRepository, never()).delete(any());
    }

    @Test
    void generateAiDraftVersion_success() {
        UUID planId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        ReflectionTestUtils.setField(plan, "status", DailyPlanStatus.READY);

        DailyPlanningContext context = planningContext(planId, 60);
        DailyPlanAiResponse response = new DailyPlanAiResponse(
                "Balanced plan",
                List.of(new DailyPlanAiResponse.AiPlanItemDto(
                        null,
                        "Task 1",
                        null,
                        DailyTaskCategory.PRACTICE,
                        30,
                        null,
                        null)),
                List.of());

        DailyPlanVersion savedDraft = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.AI_GENERATED, 60, 0);
        ReflectionTestUtils.setField(savedDraft, "id", UUID.randomUUID());

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(contextBuilder.buildContext(planId, userId)).thenReturn(context);
        when(aiGenerator.generate(context))
                .thenReturn(new DailyPlanAiGenerator.GeneratedDailyPlan(response, false));
        when(persistenceService.persistAiGeneratedDraft(
                any(),
                any(),
                any(),
                any(Integer.class),
                any(),
                any(Boolean.class),
                org.mockito.ArgumentMatchers.isNull(),
                any()))
                .thenReturn(savedDraft);
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(savedDraft.getId()))
                .thenReturn(List.of());

        DailyPlanVersionResponse result =
                dailyPlanService.generateAiDraftVersion(planId, null, userJwt);

        assertThat(result).isNotNull();
        assertThat(result.origin()).isEqualTo(DailyPlanVersionOrigin.AI_GENERATED);
        verify(aiGenerator).generate(context);
    }

    @Test
    void generateAiDraftVersion_planNotReadyOrDraft_throwsException() {
        UUID planId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        ReflectionTestUtils.setField(plan, "status", DailyPlanStatus.IN_PROGRESS);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> dailyPlanService.generateAiDraftVersion(planId, null, userJwt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot generate AI plan");
    }

    @Test
    void generateAiDraftVersion_aiProviderFailure_isPropagatedWithoutChangingVersions() {
        UUID planId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        ReflectionTestUtils.setField(plan, "status", DailyPlanStatus.DRAFT);
        DailyPlanningContext context = planningContext(planId, 60);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(contextBuilder.buildContext(planId, userId)).thenReturn(context);
        when(aiGenerator.generate(context)).thenThrow(new BusinessException(
                com.codegym.aiplanning.common.exception.ErrorCode.AI_PROVIDER_UNAVAILABLE,
                "Provider unavailable"));

        assertThatThrownBy(() -> dailyPlanService.generateAiDraftVersion(planId, null, userJwt))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Provider unavailable");
        verify(persistenceService, never()).persistAiGeneratedDraft(
                any(), any(), any(), any(Integer.class), any(), any(Boolean.class), any(), any());
    }

    @Test
    void generateAiDraftVersion_sameIdempotencyKey_returnsExistingVersion() {
        UUID planId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        ReflectionTestUtils.setField(plan, "status", DailyPlanStatus.READY);
        DailyPlanVersion existing = DailyPlanVersion.create(
                planId,
                2,
                DailyPlanVersionOrigin.AI_GENERATED,
                60,
                30);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(dailyPlanRepository.findByIdAndUserId(planId, userId))
                .thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findByDailyPlanIdAndGenerationRequestKey(
                        planId,
                        "request-123"))
                .thenReturn(Optional.of(existing));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(existing.getId()))
                .thenReturn(List.of());

        DailyPlanVersionResponse result = dailyPlanService.generateAiDraftVersion(
                planId,
                " request-123 ",
                userJwt);

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(contextBuilder, never()).buildContext(any(), any());
        verify(aiGenerator, never()).generate(any());
    }

    private DailyPlanningContext planningContext(UUID planId, int availableMinutes) {
        return new DailyPlanningContext(
                planId,
                userId,
                LocalDate.now(),
                "UTC",
                availableMinutes,
                new DailyPlanningContext.RoadmapContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "Roadmap",
                        null,
                        List.of()),
                List.of(),
                List.of(),
                List.of(),
                null);
    }
}

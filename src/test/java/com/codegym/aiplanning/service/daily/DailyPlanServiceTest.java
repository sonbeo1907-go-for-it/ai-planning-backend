package com.codegym.aiplanning.service.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyPlanRequest;
import com.codegym.aiplanning.controller.daily.dto.CreateDailyTaskRequest;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanResponse;
import com.codegym.aiplanning.controller.daily.dto.RecordPomodoroSessionRequest;
import com.codegym.aiplanning.controller.daily.dto.RecordProgressRequest;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.DailyTaskStatus;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
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
                auditLogService);

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
    void addTaskToPlan_success() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.updateActiveVersion(versionId);

        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(version, "id", versionId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId)).thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId)).thenReturn(List.of());
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> {
            DailyPlanItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(item, "createdAt", Instant.now());
            return item;
        });

        CreateDailyTaskRequest taskRequest = new CreateDailyTaskRequest("Học Java", "Đọc tài liệu", DailyTaskCategory.CUSTOM, 30, null);
        DailyPlanItemResponse response = dailyPlanService.addTaskToPlan(planId, taskRequest, userJwt);

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
        plan.activate(versionId);

        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
        ReflectionTestUtils.setField(version, "id", versionId);

        DailyPlanItem item = DailyPlanItem.create(versionId, DailyTaskCategory.CUSTOM, "Task 1", "Desc", 30, 0);
        ReflectionTestUtils.setField(item, "id", itemId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressEntryRepository.save(any(ProgressEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordProgressRequest updateReq = new RecordProgressRequest(com.codegym.aiplanning.entity.daily.ProgressEntryStatus.COMPLETED, 30, "Done", 3, 4, "Notes");
        DailyPlanItemResponse response = dailyPlanService.recordProgress(planId, itemId, updateReq, userJwt);

        assertThat(response.status()).isEqualTo(DailyTaskStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    void recordPomodoroSession_success() {
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        DailyPlan plan = DailyPlan.create(userId, LocalDate.now(), "UTC");
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.activate(versionId);

        DailyPlanVersion version = DailyPlanVersion.create(planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 0);
        ReflectionTestUtils.setField(version, "id", versionId);

        DailyPlanItem item = DailyPlanItem.create(versionId, DailyTaskCategory.CUSTOM, "Pomodoro Task", "Desc", 25, 0);
        ReflectionTestUtils.setField(item, "id", itemId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(progressEntryRepository.save(any(ProgressEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordPomodoroSessionRequest pomodoroReq = new RecordPomodoroSessionRequest(25);
        DailyPlanItemResponse response = dailyPlanService.recordPomodoroSession(planId, itemId, pomodoroReq, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(DailyTaskStatus.IN_PROGRESS);
    }

    @Test
    void createDailyPlan_withRoadmap_autoPopulatesTasks() {
        LocalDate date = LocalDate.now();
        UUID roadmapId = UUID.randomUUID();
        UUID activeVersionId = UUID.randomUUID();
        
        CreateDailyPlanRequest request = new CreateDailyPlanRequest(date, 60, roadmapId);

        UserAccount owner = UserAccount.create("owner@example.com", "Password@123", UserRole.USER, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", userId);
        
        Roadmap roadmap = Roadmap.manualDraft(owner, "Roadmap Title", "Desc");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        roadmap.activateVersion(activeVersionId);

        RoadmapVersion version = RoadmapVersion.draft(roadmap, 1, com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(version, "id", activeVersionId);

        RoadmapItem milestone = RoadmapItem.milestone(version, "Milestone 1", "Desc", 0);
        ReflectionTestUtils.setField(milestone, "id", UUID.randomUUID());

        RoadmapItem topic1 = RoadmapItem.topic(version, milestone, "Topic 1", "Desc 1", 0, 30);
        ReflectionTestUtils.setField(topic1, "id", UUID.randomUUID());

        RoadmapItem topic2 = RoadmapItem.topic(version, milestone, "Topic 2", "Desc 2", 1, 30);
        ReflectionTestUtils.setField(topic2, "id", UUID.randomUUID());

        when(dailyPlanRepository.findByUserIdAndPlanDate(userId, date)).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(roadmapRepository.findByIdAndOwnerId(roadmapId, userId)).thenReturn(Optional.of(roadmap));
        
        when(roadmapItemRepository.findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(activeVersionId, RoadmapItemType.MILESTONE))
                .thenReturn(List.of(milestone));
        when(roadmapItemRepository.findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(activeVersionId, milestone.getId()))
                .thenReturn(List.of(topic1, topic2));
        
        when(dailyPlanItemRepository.findCompletedRoadmapItemIds(userId, DailyTaskStatus.COMPLETED)).thenReturn(List.of());
        
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
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> {
            DailyPlanItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(item, "createdAt", Instant.now());
            return item;
        });

        DailyPlanResponse response = dailyPlanService.createDailyPlan(request, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.roadmapId()).isEqualTo(roadmapId);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).title()).isEqualTo("Topic 1");
        assertThat(response.items().get(0).roadmapItemId()).isEqualTo(topic1.getId());
        assertThat(response.items().get(1).title()).isEqualTo("Topic 2");
        assertThat(response.items().get(1).roadmapItemId()).isEqualTo(topic2.getId());
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
        when(dailyPlanVersionRepository.findTopByDailyPlanIdOrderByVersionNumberDesc(planId)).thenReturn(Optional.of(version));
        when(dailyPlanItemRepository.findByDailyPlanVersionIdOrderByOrderIndexAsc(versionId)).thenReturn(List.of());
        when(roadmapItemRepository.findById(roadmapItemId)).thenReturn(Optional.of(topic));
        when(dailyPlanItemRepository.save(any(DailyPlanItem.class))).thenAnswer(inv -> {
            DailyPlanItem item = inv.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(item, "createdAt", Instant.now());
            return item;
        });

        CreateDailyTaskRequest taskRequest = new CreateDailyTaskRequest("Học Java", "Đọc tài liệu", DailyTaskCategory.CUSTOM, 30, roadmapItemId);
        DailyPlanItemResponse response = dailyPlanService.addTaskToPlan(planId, taskRequest, userJwt);

        assertThat(response).isNotNull();
        assertThat(response.roadmapItemId()).isEqualTo(roadmapItemId);
        assertThat(plan.getRoadmapId()).isEqualTo(roadmapId);
    }
}

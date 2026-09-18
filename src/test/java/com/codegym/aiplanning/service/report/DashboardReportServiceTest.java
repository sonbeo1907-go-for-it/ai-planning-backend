package com.codegym.aiplanning.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.report.dto.KnowledgeMapResponse;
import com.codegym.aiplanning.controller.report.dto.WeakTopicTimelineItemDto;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.evaluation.WeakTopicStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopicTrigger;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.report.impl.DashboardReportServiceImpl;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardReportServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ProgressEntryRepository progressEntryRepository;

    @Mock
    private DailyPlanRepository dailyPlanRepository;

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private RoadmapProgressService roadmapProgressService;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapItemProgressRepository roadmapItemProgressRepository;

    @Mock
    private WeakTopicRepository weakTopicRepository;

    @InjectMocks
    private DashboardReportServiceImpl dashboardReportService;

    private UUID userId;
    private UserProfile userProfile;
    private ZoneId zoneId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        UserAccount account = UserAccount.create("learner@test.com", "Password@123", UserRole.USER, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", userId);

        userProfile = UserProfile.create(account, "Test Learner");
        ReflectionTestUtils.setField(userProfile, "timeZone", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(userProfile, "defaultDailyMinutes", 45);
        zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

        lenient().when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(userProfile));
    }

    @Test
    void getDashboardReport_withActiveToday_calculatesConsecutiveStreak() {
        LocalDate today = LocalDate.now(zoneId);
        Instant todayInstant = today.atTime(10, 0).atZone(zoneId).toInstant();
        Instant yesterdayInstant = today.minusDays(1).atTime(15, 0).atZone(zoneId).toInstant();
        Instant twoDaysAgoInstant = today.minusDays(2).atTime(18, 0).atZone(zoneId).toInstant();

        ProgressEntry entryToday = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 30, 100, "Done", 1, 5, null, null);
        ReflectionTestUtils.setField(entryToday, "recordedAt", todayInstant);

        ProgressEntry entryYesterday = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 45, 100, "Done", 2, 4, null, null);
        ReflectionTestUtils.setField(entryYesterday, "recordedAt", yesterdayInstant);

        ProgressEntry entryTwoDaysAgo = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 50, 100, "Done", 2, 4, null, null);
        ReflectionTestUtils.setField(entryTwoDaysAgo, "recordedAt", twoDaysAgoInstant);

        when(progressEntryRepository.findByUserIdAndStatus(userId, ProgressEntryStatus.COMPLETED))
                .thenReturn(List.of(entryToday, entryYesterday, entryTwoDaysAgo));
        when(dailyPlanRepository.findCompletedPlanDatesNative(userId)).thenReturn(List.of());
        when(progressEntryRepository.sumActualMinutesByUserId(userId)).thenReturn(125L);
        when(progressEntryRepository.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(eq(userId), any()))
                .thenReturn(List.of(entryTwoDaysAgo, entryYesterday, entryToday));
        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE)).thenReturn(Optional.empty());

        DashboardReportResponse response = dashboardReportService.getDashboardReport(userId);

        assertThat(response.streak().currentStreak()).isEqualTo(3);
        assertThat(response.streak().longestStreak()).isEqualTo(3);
        assertThat(response.streak().isActiveToday()).isTrue();
        assertThat(response.streak().timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.studyTime().totalStudyMinutes()).isEqualTo(125L);
        assertThat(response.studyTime().totalStudyHours()).isEqualTo(2.1);
        assertThat(response.studyTime().dailyPoints()).hasSize(7);
        assertThat(response.masterPlan()).isNull();
    }

    @Test
    void getDashboardReport_withYesterdayCompleted_preservesStreakEvenIfTodayNotCompleted() {
        LocalDate today = LocalDate.now(zoneId);
        Instant yesterdayInstant = today.minusDays(1).atTime(14, 0).atZone(zoneId).toInstant();
        Instant twoDaysAgoInstant = today.minusDays(2).atTime(16, 0).atZone(zoneId).toInstant();

        ProgressEntry entryYesterday = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 40, 100, "Done", 2, 4, null, null);
        ReflectionTestUtils.setField(entryYesterday, "recordedAt", yesterdayInstant);

        ProgressEntry entryTwoDaysAgo = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 60, 100, "Done", 2, 4, null, null);
        ReflectionTestUtils.setField(entryTwoDaysAgo, "recordedAt", twoDaysAgoInstant);

        when(progressEntryRepository.findByUserIdAndStatus(userId, ProgressEntryStatus.COMPLETED))
                .thenReturn(List.of(entryYesterday, entryTwoDaysAgo));
        when(dailyPlanRepository.findCompletedPlanDatesNative(userId)).thenReturn(List.of());
        when(progressEntryRepository.sumActualMinutesByUserId(userId)).thenReturn(100L);
        when(progressEntryRepository.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(eq(userId), any()))
                .thenReturn(List.of(entryTwoDaysAgo, entryYesterday));
        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE)).thenReturn(Optional.empty());

        DashboardReportResponse response = dashboardReportService.getDashboardReport(userId);

        assertThat(response.streak().currentStreak()).isEqualTo(2);
        assertThat(response.streak().longestStreak()).isEqualTo(2);
        assertThat(response.streak().isActiveToday()).isFalse();
    }

    @Test
    void getDashboardReport_withMissedYesterday_resetsStreakToZero() {
        LocalDate today = LocalDate.now(zoneId);
        Instant threeDaysAgoInstant = today.minusDays(3).atTime(10, 0).atZone(zoneId).toInstant();
        Instant fourDaysAgoInstant = today.minusDays(4).atTime(10, 0).atZone(zoneId).toInstant();

        ProgressEntry entry3 = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 30, 100, "Done", 1, 5, null, null);
        ReflectionTestUtils.setField(entry3, "recordedAt", threeDaysAgoInstant);

        ProgressEntry entry4 = ProgressEntry.create(
                userId, UUID.randomUUID(), ProgressEntryStatus.COMPLETED, 30, 100, "Done", 1, 5, null, null);
        ReflectionTestUtils.setField(entry4, "recordedAt", fourDaysAgoInstant);

        when(progressEntryRepository.findByUserIdAndStatus(userId, ProgressEntryStatus.COMPLETED))
                .thenReturn(List.of(entry3, entry4));
        when(dailyPlanRepository.findCompletedPlanDatesNative(userId)).thenReturn(List.of());
        when(progressEntryRepository.sumActualMinutesByUserId(userId)).thenReturn(60L);
        when(progressEntryRepository.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(eq(userId), any()))
                .thenReturn(List.of(entry4, entry3));
        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE)).thenReturn(Optional.empty());

        DashboardReportResponse response = dashboardReportService.getDashboardReport(userId);

        assertThat(response.streak().currentStreak()).isEqualTo(0);
        assertThat(response.streak().longestStreak()).isEqualTo(2);
        assertThat(response.streak().isActiveToday()).isFalse();
    }

    @Test
    void getDashboardReport_withActiveRoadmap_populatesMasterPlanProgress() {
        UUID roadmapId = UUID.randomUUID();
        UserAccount owner = userProfile.getUser();
        Roadmap roadmap = Roadmap.manualDraft(owner, "Backend Engineer Roadmap", "Description");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);

        RoadmapProgressResponse.TopicProgress topicProgress = new RoadmapProgressResponse.TopicProgress(
                UUID.randomUUID(), "Spring Framework", RoadmapItemProgressStatus.IN_PROGRESS, 50, 2, 4, List.of());
        RoadmapProgressResponse progressResponse = new RoadmapProgressResponse(
                roadmapId, UUID.randomUUID(), 1, 3, 33.3, List.of(topicProgress));

        when(progressEntryRepository.findByUserIdAndStatus(userId, ProgressEntryStatus.COMPLETED)).thenReturn(List.of());
        when(dailyPlanRepository.findCompletedPlanDatesNative(userId)).thenReturn(List.of());
        when(progressEntryRepository.sumActualMinutesByUserId(userId)).thenReturn(0L);
        when(progressEntryRepository.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(eq(userId), any()))
                .thenReturn(List.of());
        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE))
                .thenReturn(Optional.of(roadmap));
        when(roadmapProgressService.getProgress(userId, roadmapId)).thenReturn(progressResponse);

        DashboardReportResponse response = dashboardReportService.getDashboardReport(userId);

        assertThat(response.masterPlan()).isNotNull();
        assertThat(response.masterPlan().roadmapId()).isEqualTo(roadmapId);
        assertThat(response.masterPlan().title()).isEqualTo("Backend Engineer Roadmap");
        assertThat(response.masterPlan().completionPercentage()).isEqualTo(33.3);
        assertThat(response.masterPlan().completedTopics()).isEqualTo(1);
        assertThat(response.masterPlan().totalTopics()).isEqualTo(3);
        assertThat(response.masterPlan().completedLearningUnits()).isEqualTo(2);
        assertThat(response.masterPlan().totalLearningUnits()).isEqualTo(4);
    }

    @Test
    void getKnowledgeMap_withActiveRoadmap_calculatesMasteredHierarchyCorrectly() {
        UUID roadmapId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Roadmap roadmap = Roadmap.manualDraft(userProfile.getUser(), "Java Mastery Roadmap", "Backend Dev");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        ReflectionTestUtils.setField(roadmap, "activeVersionId", versionId);

        RoadmapVersion version = RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(version, "id", versionId);

        RoadmapItem milestone = RoadmapItem.milestone(version, "Phase 1: Java Core", "Core concepts", 0);
        UUID mId = UUID.randomUUID();
        ReflectionTestUtils.setField(milestone, "id", mId);

        RoadmapItem topic1 = RoadmapItem.topic(version, milestone, "OOP", "OOP Principles", 0, 120);
        UUID t1Id = UUID.randomUUID();
        ReflectionTestUtils.setField(topic1, "id", t1Id);

        RoadmapItem topic2 = RoadmapItem.topic(version, milestone, "Collections", "Java Collections", 1, 120);
        UUID t2Id = UUID.randomUUID();
        ReflectionTestUtils.setField(topic2, "id", t2Id);

        RoadmapItem unit1 = RoadmapItem.learningUnit(version, topic1, "Polymorphism", "Polymorphism deep dive", 0, 60);
        UUID u1Id = UUID.randomUUID();
        ReflectionTestUtils.setField(unit1, "id", u1Id);

        RoadmapItem unit2 = RoadmapItem.learningUnit(version, topic1, "Encapsulation", "Encapsulation details", 1, 60);
        UUID u2Id = UUID.randomUUID();
        ReflectionTestUtils.setField(unit2, "id", u2Id);

        RoadmapItem unit3 = RoadmapItem.learningUnit(version, topic2, "List & Map", "Collections Framework", 0, 60);
        UUID u3Id = UUID.randomUUID();
        ReflectionTestUtils.setField(unit3, "id", u3Id);

        RoadmapItemProgress p1 = RoadmapItemProgress.create(userId, versionId, u1Id);
        ReflectionTestUtils.setField(p1, "status", RoadmapItemProgressStatus.COMPLETED);
        ReflectionTestUtils.setField(p1, "completionPercentage", 100);
        ReflectionTestUtils.setField(p1, "completedAt", Instant.now());

        WeakTopic wt2 = WeakTopic.create(
                userProfile.getUser(), roadmap, version, unit2,
                WeakTopicTrigger.QUIZ_FAILED, new BigDecimal("45.0"), 2, Instant.now().minus(5, ChronoUnit.DAYS));
        wt2.markMastered(Instant.now());

        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE))
                .thenReturn(Optional.of(roadmap));
        when(roadmapItemRepository.findAllByRoadmapVersionIds(List.of(versionId)))
                .thenReturn(List.of(milestone, topic1, topic2, unit1, unit2, unit3));
        when(roadmapItemProgressRepository.findByUserIdAndRoadmapVersionId(userId, versionId))
                .thenReturn(List.of(p1));
        when(weakTopicRepository.findByUserIdAndRoadmapIdOrderByCreatedAtDesc(userId, roadmapId))
                .thenReturn(List.of(wt2));

        KnowledgeMapResponse response = dashboardReportService.getKnowledgeMap(userId, null);

        assertThat(response).isNotNull();
        assertThat(response.roadmapId()).isEqualTo(roadmapId);
        assertThat(response.totalMilestones()).isEqualTo(1);
        assertThat(response.totalTopics()).isEqualTo(2);
        assertThat(response.totalLearningUnits()).isEqualTo(3);
        assertThat(response.masteredLearningUnits()).isEqualTo(2);
        assertThat(response.masteredTopics()).isEqualTo(1);
        assertThat(response.masteryPercentage()).isEqualTo(66.7);
        assertThat(response.milestones()).hasSize(1);
        assertThat(response.milestones().get(0).topics()).hasSize(2);
        assertThat(response.milestones().get(0).topics().get(0).isMastered()).isTrue();
        assertThat(response.milestones().get(0).topics().get(1).isMastered()).isFalse();
    }

    @Test
    void getKnowledgeMap_whenNoRoadmap_returnsEmptyStructure() {
        when(roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(roadmapRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId))
                .thenReturn(List.of());

        KnowledgeMapResponse response = dashboardReportService.getKnowledgeMap(userId, null);

        assertThat(response).isNotNull();
        assertThat(response.roadmapId()).isNull();
        assertThat(response.totalMilestones()).isEqualTo(0);
        assertThat(response.masteredTopics()).isEqualTo(0);
        assertThat(response.milestones()).isEmpty();
    }

    @Test
    void getWeakTopicsTimeline_returnsCalculatedDaysToMaster() {
        UUID roadmapId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Roadmap roadmap = Roadmap.manualDraft(userProfile.getUser(), "Java Mastery Roadmap", "Backend Dev");
        ReflectionTestUtils.setField(roadmap, "id", roadmapId);
        RoadmapVersion version = RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.MANUAL);
        ReflectionTestUtils.setField(version, "id", versionId);

        RoadmapItem milestone = RoadmapItem.milestone(version, "Milestone 1", "", 0);
        ReflectionTestUtils.setField(milestone, "id", UUID.randomUUID());
        RoadmapItem topic = RoadmapItem.topic(version, milestone, "OOP", "", 0, 60);
        ReflectionTestUtils.setField(topic, "id", UUID.randomUUID());
        RoadmapItem unit = RoadmapItem.learningUnit(version, topic, "Polymorphism", "", 0, 60);
        ReflectionTestUtils.setField(unit, "id", UUID.randomUUID());

        Instant unresolvedAt = Instant.parse("2026-03-01T10:00:00Z");
        Instant masteredAt = Instant.parse("2026-03-05T15:00:00Z");

        WeakTopic wt = WeakTopic.create(
                userProfile.getUser(), roadmap, version, unit,
                WeakTopicTrigger.QUIZ_FAILED, new BigDecimal("40.0"), 2, unresolvedAt);
        wt.markMastered(masteredAt);

        when(weakTopicRepository.findWithContextByUserIdAndOptionalRoadmapId(userId, roadmapId))
                .thenReturn(List.of(wt));

        List<WeakTopicTimelineItemDto> timeline = dashboardReportService.getWeakTopicsTimeline(userId, roadmapId);

        assertThat(timeline).hasSize(1);
        WeakTopicTimelineItemDto item = timeline.get(0);
        assertThat(item.learningUnitTitle()).isEqualTo("Polymorphism");
        assertThat(item.topicTitle()).isEqualTo("OOP");
        assertThat(item.milestoneTitle()).isEqualTo("Milestone 1");
        assertThat(item.status()).isEqualTo(WeakTopicStatus.MASTERED);
        assertThat(item.daysToMaster()).isEqualTo(4L);
    }
}

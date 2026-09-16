package com.codegym.aiplanning.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.report.impl.DashboardReportServiceImpl;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
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

        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(userProfile));
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
}

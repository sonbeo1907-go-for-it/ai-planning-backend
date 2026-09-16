package com.codegym.aiplanning.service.report.impl;

import com.codegym.aiplanning.controller.report.dto.DailyStudyTimePointDto;
import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.report.dto.MasterPlanProgressDto;
import com.codegym.aiplanning.controller.report.dto.StreakDto;
import com.codegym.aiplanning.controller.report.dto.StudyTimeDto;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.report.DashboardReportService;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardReportServiceImpl implements DashboardReportService {

    private final UserProfileRepository userProfileRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapProgressService roadmapProgressService;

    public DashboardReportServiceImpl(
            UserProfileRepository userProfileRepository,
            ProgressEntryRepository progressEntryRepository,
            DailyPlanRepository dailyPlanRepository,
            RoadmapRepository roadmapRepository,
            RoadmapProgressService roadmapProgressService) {
        this.userProfileRepository = userProfileRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapProgressService = roadmapProgressService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardReportResponse getDashboardReport(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        String timeZoneStr = profile != null && profile.getTimeZone() != null ? profile.getTimeZone() : "UTC";
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timeZoneStr);
        } catch (Exception e) {
            zoneId = ZoneId.of("UTC");
            timeZoneStr = "UTC";
        }
        int targetMinutes = profile != null ? profile.getDefaultDailyMinutes() : 60;
        LocalDate today = LocalDate.now(zoneId);
        LocalDate yesterday = today.minusDays(1);

        // 1. Calculate Streak
        StreakDto streak = calculateStreak(userId, zoneId, timeZoneStr, today, yesterday);

        // 2. Calculate Study Time & 7-Day History
        StudyTimeDto studyTime = calculateStudyTime(userId, zoneId, today, targetMinutes);

        // 3. Calculate Master Plan Progress
        MasterPlanProgressDto masterPlan = calculateMasterPlan(userId);

        return new DashboardReportResponse(masterPlan, streak, studyTime);
    }

    private StreakDto calculateStreak(
            UUID userId, ZoneId zoneId, String timeZoneStr, LocalDate today, LocalDate yesterday) {
        List<ProgressEntry> completedEntries =
                progressEntryRepository.findByUserIdAndStatus(userId, ProgressEntryStatus.COMPLETED);
        List<Date> planDates = dailyPlanRepository.findCompletedPlanDatesNative(userId);

        TreeSet<LocalDate> completedDates = new TreeSet<>();
        for (ProgressEntry entry : completedEntries) {
            if (entry.getRecordedAt() != null) {
                completedDates.add(entry.getRecordedAt().atZone(zoneId).toLocalDate());
            }
        }
        for (Date sqlDate : planDates) {
            if (sqlDate != null) {
                completedDates.add(sqlDate.toLocalDate());
            }
        }

        int currentStreak = 0;
        boolean isActiveToday = completedDates.contains(today);
        LocalDate lastActiveDate = completedDates.isEmpty() ? null : completedDates.last();

        if (isActiveToday) {
            currentStreak = 1;
            LocalDate checkDate = today.minusDays(1);
            while (completedDates.contains(checkDate)) {
                currentStreak++;
                checkDate = checkDate.minusDays(1);
            }
        } else if (completedDates.contains(yesterday)) {
            currentStreak = 1;
            LocalDate checkDate = yesterday.minusDays(1);
            while (completedDates.contains(checkDate)) {
                currentStreak++;
                checkDate = checkDate.minusDays(1);
            }
        } else {
            currentStreak = 0;
        }

        int longestStreak = 0;
        if (!completedDates.isEmpty()) {
            int tempStreak = 0;
            LocalDate prevDate = null;
            for (LocalDate d : completedDates) {
                if (prevDate == null || d.equals(prevDate.plusDays(1))) {
                    tempStreak++;
                } else if (d.isAfter(prevDate.plusDays(1))) {
                    tempStreak = 1;
                }
                prevDate = d;
                if (tempStreak > longestStreak) {
                    longestStreak = tempStreak;
                }
            }
        }
        longestStreak = Math.max(longestStreak, currentStreak);

        return new StreakDto(currentStreak, longestStreak, isActiveToday, lastActiveDate, timeZoneStr);
    }

    private StudyTimeDto calculateStudyTime(
            UUID userId, ZoneId zoneId, LocalDate today, int targetMinutes) {
        Long totalMinutesSum = progressEntryRepository.sumActualMinutesByUserId(userId);
        long totalStudyMinutes = totalMinutesSum != null ? totalMinutesSum : 0L;
        double totalStudyHours = Math.round((totalStudyMinutes / 60.0) * 10.0) / 10.0;

        LocalDate sevenDaysAgo = today.minusDays(6);
        Instant sevenDaysAgoInstant = sevenDaysAgo.atStartOfDay(zoneId).toInstant();
        List<ProgressEntry> recentEntries = progressEntryRepository
                .findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(userId, sevenDaysAgoInstant);

        Map<LocalDate, Integer> dailyMinutesMap = new HashMap<>();
        Map<LocalDate, Set<UUID>> dailyTasksMap = new HashMap<>();

        for (ProgressEntry entry : recentEntries) {
            if (entry.getRecordedAt() != null) {
                LocalDate entryDate = entry.getRecordedAt().atZone(zoneId).toLocalDate();
                if (!entryDate.isBefore(sevenDaysAgo) && !entryDate.isAfter(today)) {
                    int minutes = entry.getActualMinutes() != null ? entry.getActualMinutes() : 0;
                    dailyMinutesMap.put(entryDate, dailyMinutesMap.getOrDefault(entryDate, 0) + minutes);
                    if (entry.getStatus() == ProgressEntryStatus.COMPLETED && entry.getDailyPlanItemId() != null) {
                        dailyTasksMap.computeIfAbsent(entryDate, k -> new HashSet<>()).add(entry.getDailyPlanItemId());
                    }
                }
            }
        }

        List<DailyStudyTimePointDto> dailyPoints = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String dayOfWeek = formatDayOfWeek(d);
            int minutes = dailyMinutesMap.getOrDefault(d, 0);
            int completedCount = dailyTasksMap.containsKey(d) ? dailyTasksMap.get(d).size() : 0;
            dailyPoints.add(new DailyStudyTimePointDto(d, dayOfWeek, minutes, completedCount, targetMinutes));
        }

        return new StudyTimeDto(totalStudyMinutes, totalStudyHours, dailyPoints);
    }

    private MasterPlanProgressDto calculateMasterPlan(UUID userId) {
        Optional<Roadmap> activeRoadmapOpt = roadmapRepository.findByOwnerIdAndStatus(userId, RoadmapStatus.ACTIVE);
        if (activeRoadmapOpt.isEmpty()) {
            return null;
        }

        Roadmap roadmap = activeRoadmapOpt.get();
        RoadmapProgressResponse progress = roadmapProgressService.getProgress(userId, roadmap.getId());
        int completedUnits = 0;
        int totalUnits = 0;
        if (progress.topics() != null) {
            for (RoadmapProgressResponse.TopicProgress tp : progress.topics()) {
                completedUnits += tp.completedLearningUnits();
                totalUnits += tp.totalLearningUnits();
            }
        }

        return new MasterPlanProgressDto(
                roadmap.getId(),
                roadmap.getTitle(),
                progress.completionPercentage(),
                progress.completedTopics(),
                progress.totalTopics(),
                completedUnits,
                totalUnits);
    }

    private String formatDayOfWeek(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }
}

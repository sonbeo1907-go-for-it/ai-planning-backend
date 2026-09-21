package com.codegym.aiplanning.service.report.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.report.dto.DailyStudyTimePointDto;
import com.codegym.aiplanning.controller.report.dto.DashboardReportResponse;
import com.codegym.aiplanning.controller.report.dto.KnowledgeLearningUnitDto;
import com.codegym.aiplanning.controller.report.dto.KnowledgeMapResponse;
import com.codegym.aiplanning.controller.report.dto.KnowledgeMilestoneDto;
import com.codegym.aiplanning.controller.report.dto.KnowledgeTopicDto;
import com.codegym.aiplanning.controller.report.dto.MasterPlanProgressDto;
import com.codegym.aiplanning.controller.report.dto.StreakDto;
import com.codegym.aiplanning.controller.report.dto.StudyTimeDto;
import com.codegym.aiplanning.controller.report.dto.WeakTopicTimelineItemDto;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapProgressResponse;
import com.codegym.aiplanning.entity.daily.ProgressEntry;
import com.codegym.aiplanning.entity.daily.ProgressEntryStatus;
import com.codegym.aiplanning.entity.evaluation.WeakTopic;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgress;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemProgressStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.repository.daily.ProgressEntryRepository;
import com.codegym.aiplanning.repository.evaluation.WeakTopicRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemProgressRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.service.report.DashboardReportService;
import com.codegym.aiplanning.service.roadmap.RoadmapProgressService;
import com.codegym.aiplanning.service.roadmap.progress.ProgressHistoryResolver;
import com.codegym.aiplanning.service.roadmap.progress.ProgressHistoryResolver.EffectiveEvent;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardReportServiceImpl implements DashboardReportService {

    private final UserProfileRepository userProfileRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapProgressService roadmapProgressService;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapItemProgressRepository roadmapItemProgressRepository;
    private final WeakTopicRepository weakTopicRepository;

    public DashboardReportServiceImpl(
            UserProfileRepository userProfileRepository,
            ProgressEntryRepository progressEntryRepository,
            RoadmapRepository roadmapRepository,
            RoadmapProgressService roadmapProgressService,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapItemProgressRepository roadmapItemProgressRepository,
            WeakTopicRepository weakTopicRepository) {
        this.userProfileRepository = userProfileRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapProgressService = roadmapProgressService;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapItemProgressRepository = roadmapItemProgressRepository;
        this.weakTopicRepository = weakTopicRepository;
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
        Instant now = Instant.now();
        LocalDate today = now.atZone(zoneId).toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        List<EffectiveEvent> events = ProgressHistoryResolver.effectiveEvents(
                        progressEntryRepository.findByUserIdOrderByRecordedAtDesc(userId))
                .stream()
                .filter(event -> event.activityRecordedAt() != null
                        && !event.activityRecordedAt().isAfter(now))
                .toList();

        StreakDto streak = calculateStreak(events, zoneId, timeZoneStr, today, yesterday);

        StudyTimeDto studyTime = calculateStudyTime(events, zoneId, today, targetMinutes);

        MasterPlanProgressDto masterPlan = calculateMasterPlan(userId);

        return new DashboardReportResponse(masterPlan, streak, studyTime);
    }

    private StreakDto calculateStreak(
            List<EffectiveEvent> events, ZoneId zoneId, String timeZoneStr,
            LocalDate today, LocalDate yesterday) {
        TreeSet<LocalDate> completedDates = new TreeSet<>();
        for (EffectiveEvent event : events) {
            if (isCompletedTask(event.entry())) {
                completedDates.add(event.activityRecordedAt().atZone(zoneId).toLocalDate());
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
            List<EffectiveEvent> events, ZoneId zoneId, LocalDate today, int targetMinutes) {
        long totalStudyMinutes = 0;
        LocalDate sevenDaysAgo = today.minusDays(6);

        Map<LocalDate, Integer> dailyMinutesMap = new HashMap<>();
        Map<LocalDate, Set<UUID>> dailyTasksMap = new HashMap<>();

        for (EffectiveEvent event : events) {
            ProgressEntry entry = event.entry();
            int minutes = entry.getActualMinutes() != null ? entry.getActualMinutes() : 0;
            totalStudyMinutes += minutes;
            LocalDate entryDate = event.activityRecordedAt().atZone(zoneId).toLocalDate();
            if (!entryDate.isBefore(sevenDaysAgo) && !entryDate.isAfter(today)) {
                dailyMinutesMap.merge(entryDate, minutes, Integer::sum);
                if (isCompletedTask(entry)) {
                    dailyTasksMap.computeIfAbsent(entryDate, ignored -> new HashSet<>())
                            .add(entry.getDailyPlanItemId());
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

        double totalStudyHours = Math.round((totalStudyMinutes / 60.0) * 10.0) / 10.0;
        return new StudyTimeDto(totalStudyMinutes, totalStudyHours, dailyPoints);
    }

    private boolean isCompletedTask(ProgressEntry entry) {
        return entry.getStatus() == ProgressEntryStatus.COMPLETED
                && entry.getDailyPlanItemId() != null;
    }

    private MasterPlanProgressDto calculateMasterPlan(UUID userId) {
        Optional<Roadmap> activeRoadmapOpt = roadmapRepository
                .findFirstByOwnerIdAndStatusOrderByUpdatedAtDescIdAsc(userId, RoadmapStatus.ACTIVE);
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

    @Override
    @Transactional(readOnly = true)
    public KnowledgeMapResponse getKnowledgeMap(UUID userId, UUID roadmapId) {
        Roadmap roadmap;
        if (roadmapId != null) {
            roadmap = roadmapRepository.findByIdAndOwnerId(roadmapId, userId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "Roadmap was not found."));
        } else {
            roadmap = roadmapRepository.findFirstByOwnerIdAndStatusOrderByUpdatedAtDescIdAsc(
                            userId, RoadmapStatus.ACTIVE)
                    .orElse(null);
            if (roadmap == null) {
                List<Roadmap> allRoadmaps = roadmapRepository.findAllByOwnerIdOrderByUpdatedAtDesc(userId);
                if (!allRoadmaps.isEmpty()) {
                    roadmap = allRoadmaps.get(0);
                }
            }
        }

        if (roadmap == null || roadmap.getActiveVersionId() == null) {
            return new KnowledgeMapResponse(
                    roadmap != null ? roadmap.getId() : null,
                    roadmap != null ? roadmap.getTitle() : null,
                    0, 0, 0, 0, 0, 0.0,
                    List.of());
        }

        UUID versionId = roadmap.getActiveVersionId();
        List<RoadmapItem> items = roadmapItemRepository.findAllByRoadmapVersionIds(List.of(versionId));
        List<RoadmapItemProgress> progressList = roadmapItemProgressRepository.findByUserIdAndRoadmapVersionId(userId, versionId);
        Map<UUID, RoadmapItemProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(RoadmapItemProgress::getRoadmapItemId, Function.identity(), (a, b) -> a));

        List<RoadmapItem> milestones = items.stream()
                .filter(i -> i.getItemType() == RoadmapItemType.MILESTONE)
                .sorted(Comparator.comparingInt(RoadmapItem::getOrderIndex))
                .toList();

        Map<UUID, List<RoadmapItem>> topicsByMilestoneId = items.stream()
                .filter(i -> i.getItemType() == RoadmapItemType.TOPIC && i.getParent() != null)
                .sorted(Comparator.comparingInt(RoadmapItem::getOrderIndex))
                .collect(Collectors.groupingBy(i -> i.getParent().getId()));

        Map<UUID, List<RoadmapItem>> unitsByTopicId = items.stream()
                .filter(i -> i.getItemType() == RoadmapItemType.LEARNING_UNIT && i.getParent() != null)
                .sorted(Comparator.comparingInt(RoadmapItem::getOrderIndex))
                .collect(Collectors.groupingBy(i -> i.getParent().getId()));

        int totalMilestones = milestones.size();
        int totalTopics = 0;
        int masteredTopics = 0;
        int totalLearningUnits = 0;
        int masteredLearningUnits = 0;

        List<KnowledgeMilestoneDto> milestoneDtos = new ArrayList<>();

        for (RoadmapItem milestone : milestones) {
            List<RoadmapItem> topics = topicsByMilestoneId.getOrDefault(milestone.getId(), List.of());
            List<KnowledgeTopicDto> topicDtos = new ArrayList<>();

            for (RoadmapItem topic : topics) {
                totalTopics++;
                List<RoadmapItem> units = unitsByTopicId.getOrDefault(topic.getId(), List.of());
                List<KnowledgeLearningUnitDto> unitDtos = new ArrayList<>();
                int topicMasteredUnits = 0;

                for (RoadmapItem unit : units) {
                    totalLearningUnits++;
                    RoadmapItemProgress unitProg = progressMap.get(unit.getId());

                    // Reinforcement mastery is not completion of the planned Learning Unit.
                    boolean isUnitMastered = unitProg != null
                            && unitProg.getStatus() == RoadmapItemProgressStatus.COMPLETED;

                    String unitStatus = isUnitMastered
                            ? "MASTERED"
                            : (unitProg != null && unitProg.getStatus() == RoadmapItemProgressStatus.IN_PROGRESS)
                                    ? "IN_PROGRESS"
                                    : "NOT_STARTED";

                    Instant masteredAt = isUnitMastered ? unitProg.getCompletedAt() : null;

                    if (isUnitMastered) {
                        masteredLearningUnits++;
                        topicMasteredUnits++;
                    }

                    unitDtos.add(new KnowledgeLearningUnitDto(
                            unit.getId(),
                            unit.getTitle(),
                            unit.getOrderIndex(),
                            unit.getEstimatedMinutes() != null ? unit.getEstimatedMinutes() : 0,
                            isUnitMastered,
                            unitStatus,
                            masteredAt));
                }

                RoadmapItemProgress topicProg = progressMap.get(topic.getId());
                boolean isTopicMastered = (!units.isEmpty() && topicMasteredUnits == units.size())
                        || (units.isEmpty() && topicProg != null && topicProg.getStatus() == RoadmapItemProgressStatus.COMPLETED);

                int topicCompletionPercentage = units.isEmpty()
                        ? (topicProg != null ? topicProg.getCompletionPercentage() : 0)
                        : (int) Math.round((double) topicMasteredUnits * 100.0 / units.size());

                String topicStatus = isTopicMastered
                        ? "MASTERED"
                        : (topicCompletionPercentage > 0 ? "IN_PROGRESS" : "NOT_STARTED");

                Instant topicMasteredAt = isTopicMastered && topicProg != null ? topicProg.getCompletedAt() : null;

                if (isTopicMastered) {
                    masteredTopics++;
                }

                topicDtos.add(new KnowledgeTopicDto(
                        topic.getId(),
                        topic.getTitle(),
                        topic.getDescription(),
                        topic.getOrderIndex(),
                        topic.getEstimatedMinutes() != null ? topic.getEstimatedMinutes() : 0,
                        isTopicMastered,
                        topicStatus,
                        topicCompletionPercentage,
                        topicMasteredAt,
                        unitDtos));
            }

            milestoneDtos.add(new KnowledgeMilestoneDto(
                    milestone.getId(),
                    milestone.getTitle(),
                    milestone.getDescription(),
                    milestone.getOrderIndex(),
                    topicDtos));
        }

        double masteryPercentage = totalLearningUnits > 0
                ? Math.round((double) masteredLearningUnits * 1000.0 / totalLearningUnits) / 10.0
                : (totalTopics > 0 ? Math.round((double) masteredTopics * 1000.0 / totalTopics) / 10.0 : 0.0);

        return new KnowledgeMapResponse(
                roadmap.getId(),
                roadmap.getTitle(),
                totalMilestones,
                totalTopics,
                masteredTopics,
                totalLearningUnits,
                masteredLearningUnits,
                masteryPercentage,
                milestoneDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeakTopicTimelineItemDto> getWeakTopicsTimeline(UUID userId, UUID roadmapId) {
        List<WeakTopic> weakTopics = weakTopicRepository.findWithContextByUserIdAndOptionalRoadmapId(userId, roadmapId);

        return weakTopics.stream()
                .map(wt -> {
                    RoadmapItem item = wt.getRoadmapItem();
                    RoadmapItem topic = item.getParent();
                    RoadmapItem milestone = topic != null ? topic.getParent() : null;

                    Long daysToMaster = null;
                    if (wt.getMasteredAt() != null && wt.getUnresolvedAt() != null) {
                        ZoneId zone = ZoneId.of(wt.getEligibilityZone());
                        daysToMaster = ChronoUnit.DAYS.between(
                                wt.getUnresolvedAt().atZone(zone).toLocalDate(),
                                wt.getMasteredAt().atZone(zone).toLocalDate());
                        if (daysToMaster < 0) {
                            daysToMaster = 0L;
                        }
                    }

                    return new WeakTopicTimelineItemDto(
                            wt.getId(),
                            wt.getRoadmap().getId(),
                            wt.getRoadmap().getTitle(),
                            item.getId(),
                            item.getTitle(),
                            topic != null ? topic.getId() : null,
                            topic != null ? topic.getTitle() : null,
                            milestone != null ? milestone.getId() : null,
                            milestone != null ? milestone.getTitle() : null,
                            wt.getStatus(),
                            wt.getTriggerSource(),
                            wt.getLastQuizScore(),
                            wt.getLastUnderstandingRating(),
                            wt.getUnresolvedAt(),
                            wt.getMasteredAt(),
                            daysToMaster);
                })
                .toList();
    }
}

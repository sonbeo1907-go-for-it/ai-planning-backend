package com.codegym.aiplanning.service.roadmap.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapItemResponse;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapSource;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.entity.source.LearningSource;
import com.codegym.aiplanning.entity.source.LearningSourceStatus;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.AiRoadmapGeneratorService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiRoadmapGeneratorServiceImpl implements AiRoadmapGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiRoadmapGeneratorServiceImpl.class);

    private static final int MAX_RETRY_ATTEMPTS = 2; // Total 3 attempts (1 initial + 2 retries)

    private final RoadmapRepository roadmapRepository;
    private final RoadmapVersionRepository roadmapVersionRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final RoadmapSourceRepository roadmapSourceRepository;
    private final AiClientService aiClientService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AiRoadmapGeneratorServiceImpl(
            RoadmapRepository roadmapRepository,
            RoadmapVersionRepository roadmapVersionRepository,
            RoadmapItemRepository roadmapItemRepository,
            RoadmapSourceRepository roadmapSourceRepository,
            AiClientService aiClientService,
            AuditLogService auditLogService) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapVersionRepository = roadmapVersionRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.roadmapSourceRepository = roadmapSourceRepository;
        this.aiClientService = aiClientService;
        this.auditLogService = auditLogService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public RoadmapVersionResponse generate(UUID userId, UUID roadmapId) {
        Roadmap roadmap = requireOwnedRoadmapForUpdate(userId, roadmapId);

        if (roadmap.getStatus() == RoadmapStatus.ONBOARDING) {
            roadmapSourceRepository.findGoalSourceByRoadmapId(roadmap.getId()).ifPresent(link -> {
                if (link.getLearningSource() != null) {
                    LearningSource ls = link.getLearningSource();
                    if (ls.getContentText() == null || ls.getContentText().isBlank()) {
                        String defaultGoal = roadmap.getTitle() != null && !roadmap.getTitle().isBlank()
                                ? roadmap.getTitle()
                                : "Học tập và phát triển kỹ năng";
                        ls.updateGoal(defaultGoal);
                    }
                    ls.markReady();
                }
            });
            roadmap.completeOnboarding(java.time.Instant.now());
            roadmapRepository.save(roadmap);
        }

        GeneratedMasterPlan plan = generateAndValidatePlan(roadmap, null);
        RoadmapVersion version = saveGeneratedVersion(roadmap, plan, false);

        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_VERSION_GENERATED_BY_AI,
                "RoadmapVersion",
                version.getId().toString());

        return getVersionResponse(roadmap.getId(), version.getId());
    }

    @Override
    @Transactional
    public RoadmapVersionResponse regenerate(UUID userId, UUID roadmapId, String adjustmentPrompt) {
        Roadmap roadmap = requireOwnedRoadmapForUpdate(userId, roadmapId);

        GeneratedMasterPlan plan = generateAndValidatePlan(roadmap, adjustmentPrompt);
        RoadmapVersion version = saveGeneratedVersion(roadmap, plan, true);

        auditLogService.logAction(
                userId,
                roadmap.getOwner().getEmail(),
                AuditEventAction.ROADMAP_VERSION_REGENERATED_BY_AI,
                "RoadmapVersion",
                version.getId().toString());

        return getVersionResponse(roadmap.getId(), version.getId());
    }

    private GeneratedMasterPlan generateAndValidatePlan(Roadmap roadmap, String adjustmentPrompt) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(roadmap, adjustmentPrompt);

        for (int attempt = 0; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.info("Requesting AI Master Plan generation (Attempt {}/{}) for Roadmap {}",
                        attempt + 1, MAX_RETRY_ATTEMPTS + 1, roadmap.getId());

                String rawResponse = aiClientService.generateContent(systemPrompt, userPrompt);
                GeneratedMasterPlan parsedPlan = parseAndValidate(rawResponse);
                if (parsedPlan != null) {
                    return parsedPlan;
                }
            } catch (Exception e) {
                log.warn("AI generation attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        throw new BusinessException(
                ErrorCode.AI_GENERATION_FAILED,
                "AI failed to generate a valid Roadmap Master Plan after " + (MAX_RETRY_ATTEMPTS + 1) + " attempts.");
    }

    private String buildSystemPrompt() {
        return """
                You are an expert AI Educational Content & Master Plan Architect.
                Your goal is to break down the user's learning goal and source materials into a structured Master Plan consisting of Milestones (Cột mốc) and Topics (Chủ đề).

                STRICT OUTPUT RULES:
                1. Return ONLY raw JSON without markdown formatting or backticks.
                2. Structure must follow this exact JSON Schema:
                   {
                     "title": "Roadmap Title",
                     "description": "Roadmap Description",
                     "milestones": [
                       {
                         "title": "Milestone Title",
                         "description": "Milestone Description",
                         "orderIndex": 0,
                         "topics": [
                           {
                             "title": "Topic Title",
                             "description": "Topic Description",
                             "orderIndex": 0,
                             "estimatedMinutes": 60
                           }
                         ]
                       }
                     ]
                   }
                3. REQUIRED CONSTRAINTS:
                   - Generate between 3 to 6 Milestones.
                   - Generate between 2 to 5 Topics inside EACH Milestone.
                   - 'estimatedMinutes' for each topic must be an integer > 0 (typically between 30 and 180).
                   - All titles and descriptions must be clear, actionable, and in Vietnamese.
                """;
    }

    private String buildUserPrompt(Roadmap roadmap, String adjustmentPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mục tiêu học tập: ").append(roadmap.getTitle() != null ? roadmap.getTitle() : "Chưa xác định").append("\n");
        sb.append("Trình độ hiện tại: ").append(roadmap.getProficiencyLevel() != null ? roadmap.getProficiencyLevel() : "BEGINNER").append("\n");
        sb.append("Thời gian cam kết hàng ngày: ").append(roadmap.getDailyCommitmentMinutes() != null ? roadmap.getDailyCommitmentMinutes() : 60).append(" phút/ngày\n");
        sb.append("Thời lượng kỳ vọng tổng thể: ").append(roadmap.getExpectedDurationDays() != null ? roadmap.getExpectedDurationDays() : 60).append(" ngày\n");

        List<RoadmapSource> sources = roadmapSourceRepository.findByRoadmapId(roadmap.getId());
        if (!sources.isEmpty()) {
            sb.append("\nTài liệu nguồn tham khảo:\n");
            for (RoadmapSource source : sources) {
                LearningSource ls = source.getLearningSource();
                if (ls != null && ls.getStatus() == LearningSourceStatus.READY && ls.getContentText() != null && !ls.getContentText().isBlank()) {
                    String snippet = ls.getContentText();
                    if (snippet.length() > 1000) {
                        snippet = snippet.substring(0, 1000) + "... [cắt bớt]";
                    }
                    sb.append("- Nguồn tài liệu: ").append(snippet).append("\n");
                }
            }
        }

        if (adjustmentPrompt != null && !adjustmentPrompt.isBlank()) {
            sb.append("\nYÊU CẦU ĐIỀU CHỈNH KHI TÁI TẠO (REGENERATE):\n");
            sb.append(adjustmentPrompt.trim()).append("\n");
        }

        return sb.toString();
    }

    private GeneratedMasterPlan parseAndValidate(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String json = rawResponse.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        try {
            GeneratedMasterPlan plan = objectMapper.readValue(json, GeneratedMasterPlan.class);

            if (plan.title == null || plan.title.isBlank()) return null;
            if (plan.milestones == null || plan.milestones.isEmpty() || plan.milestones.size() > 10) return null;

            for (GeneratedMilestone milestone : plan.milestones) {
                if (milestone.title == null || milestone.title.isBlank()) return null;
                if (milestone.topics == null || milestone.topics.isEmpty()) return null;

                for (GeneratedTopic topic : milestone.topics) {
                    if (topic.title == null || topic.title.isBlank()) return null;
                    if (topic.estimatedMinutes == null || topic.estimatedMinutes <= 0) {
                        topic.estimatedMinutes = 60; // Default fallback
                    }
                }
            }

            return plan;
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response: {}", e.getMessage());
            return null;
        }
    }

    private RoadmapVersion saveGeneratedVersion(Roadmap roadmap, GeneratedMasterPlan plan, boolean isRegenerate) {
        roadmapVersionRepository.findByRoadmapIdAndStatus(roadmap.getId(), com.codegym.aiplanning.entity.roadmap.RoadmapVersionStatus.DRAFT)
                .ifPresent(existingDraft -> {
                    roadmapVersionRepository.delete(existingDraft);
                    roadmapVersionRepository.flush();
                });

        int nextVersionNumber = roadmapVersionRepository
                .findFirstByRoadmapIdOrderByVersionNumberDesc(roadmap.getId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        RoadmapVersionOrigin origin = isRegenerate ? RoadmapVersionOrigin.AI_REGENERATED : RoadmapVersionOrigin.AI_GENERATED;
        RoadmapVersion version = RoadmapVersion.draft(roadmap, nextVersionNumber, origin);
        RoadmapVersion savedVersion = roadmapVersionRepository.saveAndFlush(version);

        int milestoneOrder = 0;
        for (GeneratedMilestone gMilestone : plan.milestones) {
            RoadmapItem milestone = RoadmapItem.milestone(
                    savedVersion,
                    gMilestone.title.trim(),
                    gMilestone.description != null ? gMilestone.description.trim() : null,
                    gMilestone.orderIndex != null ? gMilestone.orderIndex : milestoneOrder
            );
            RoadmapItem savedMilestone = roadmapItemRepository.save(milestone);
            milestoneOrder++;

            int topicOrder = 0;
            for (GeneratedTopic gTopic : gMilestone.topics) {
                RoadmapItem topic = RoadmapItem.topic(
                        savedVersion,
                        savedMilestone,
                        gTopic.title.trim(),
                        gTopic.description != null ? gTopic.description.trim() : null,
                        gTopic.orderIndex != null ? gTopic.orderIndex : topicOrder,
                        gTopic.estimatedMinutes != null ? gTopic.estimatedMinutes : 60
                );
                roadmapItemRepository.save(topic);
                topicOrder++;
            }
        }

        roadmapItemRepository.flush();
        return savedVersion;
    }

    private RoadmapVersionResponse getVersionResponse(UUID roadmapId, UUID versionId) {
        RoadmapVersion version = roadmapVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap version not found"));
        
        List<RoadmapItem> milestones = roadmapItemRepository.findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(versionId, RoadmapItemType.MILESTONE);
        List<RoadmapItemResponse> milestoneResponses = new ArrayList<>();
        for (RoadmapItem milestone : milestones) {
            List<RoadmapItem> topics = roadmapItemRepository.findAllByRoadmapVersionIdAndParentIdOrderByOrderIndexAsc(versionId, milestone.getId());
            List<RoadmapItemResponse> topicResponses = topics.stream().map(t -> RoadmapItemResponse.from(t, List.of())).toList();
            milestoneResponses.add(RoadmapItemResponse.from(milestone, topicResponses));
        }

        return RoadmapVersionResponse.from(version, milestoneResponses);
    }

    private Roadmap requireOwnedRoadmapForUpdate(UUID userId, UUID roadmapId) {
        return roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Roadmap not found or not owned by user."));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeneratedMasterPlan {
        public String title;
        public String description;
        public List<GeneratedMilestone> milestones;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeneratedMilestone {
        public String title;
        public String description;
        public Integer orderIndex;
        public List<GeneratedTopic> topics;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeneratedTopic {
        public String title;
        public String description;
        public Integer orderIndex;
        public Integer estimatedMinutes;
    }
}

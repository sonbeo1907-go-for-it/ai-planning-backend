package com.codegym.aiplanning.controller.daily.dto;

import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "One exact immutable or editable Daily Plan version")
public record DailyPlanVersionResponse(
        UUID id,
        long entityVersion,
        UUID dailyPlanId,
        Integer versionNumber,
        DailyPlanVersionStatus status,
        DailyPlanVersionOrigin origin,
        Integer availableMinutes,
        Integer totalPlannedMinutes,
        Instant activatedAt,
        Instant supersededAt,
        List<DailyPlanItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        String aiExplanation,
        Boolean requiresUserDecision) {

    public static DailyPlanVersionResponse from(
            DailyPlanVersion version, List<DailyPlanItem> items) {
        return new DailyPlanVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getDailyPlanId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getOrigin(),
                version.getAvailableMinutes(),
                version.getTotalPlannedMinutes(),
                version.getActivatedAt(),
                version.getSupersededAt(),
                items == null
                        ? List.of()
                        : items.stream().map(DailyPlanItemResponse::from).toList(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getAiExplanation(),
                version.getRequiresUserDecision());
    }

    public static DailyPlanVersionResponse of(
            DailyPlanVersion version, List<DailyPlanItemResponse> items) {
        return new DailyPlanVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getDailyPlanId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getOrigin(),
                version.getAvailableMinutes(),
                version.getTotalPlannedMinutes(),
                version.getActivatedAt(),
                version.getSupersededAt(),
                items == null ? List.of() : items,
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getAiExplanation(),
                version.getRequiresUserDecision());
    }

    @Override
    public String toString() {
        return "DailyPlanVersionResponse[id=" + id
                + ", versionNumber=" + versionNumber
                + ", status=" + status
                + ", personalLearningData=<redacted>]";
    }
}

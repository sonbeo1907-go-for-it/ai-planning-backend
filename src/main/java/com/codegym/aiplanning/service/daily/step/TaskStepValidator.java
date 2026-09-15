package com.codegym.aiplanning.service.daily.step;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.daily.DailyPlanTaskStep;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskStepValidator {

    static final int MAX_TITLE_LENGTH = 255;
    static final int MAX_GUIDANCE_LENGTH = 4000;

    public void validateAll(
            List<DailyPlanTaskStep> steps,
            int parentPlannedMinutes,
            String parentTaskTitle) {
        Set<String> normalizedTitles = new HashSet<>();
        int estimatedTotal = 0;
        int requiredCount = 0;
        String normalizedParentTitle = normalizeTitle(parentTaskTitle);

        for (DailyPlanTaskStep step : steps) {
            validateText(step.getTitle(), step.getGuidance());
            String normalizedTitle = normalizeTitle(step.getTitle());
            if (normalizedTitle.equals(normalizedParentTitle)) {
                throw invalid("A Task Step must not repeat the parent task title.");
            }
            if (!normalizedTitles.add(normalizedTitle)) {
                throw invalid("Task Step titles must be unique within one Daily Plan task.");
            }
            if (step.getOrderIndex() == null || step.getOrderIndex() < 0) {
                throw invalid("Task Step order index must not be negative.");
            }
            if (step.getEstimatedMinutes() != null) {
                if (step.getEstimatedMinutes() <= 0) {
                    throw invalid("Task Step estimated minutes must be positive when present.");
                }
                estimatedTotal += step.getEstimatedMinutes();
            }
            if (step.getRequired() == null) {
                throw invalid("Task Step required flag is required.");
            }
            if (Boolean.TRUE.equals(step.getRequired())) {
                requiredCount++;
            }
        }

        if (!steps.isEmpty() && requiredCount == 0) {
            throw invalid("A non-empty Task Step list must contain at least one required step.");
        }
        if (estimatedTotal > parentPlannedMinutes) {
            throw new BusinessException(
                    ErrorCode.TASK_STEP_TIME_EXCEEDED,
                    "Task Step estimates exceed the parent task's planned minutes.");
        }
    }

    public String normalizeRequiredTitle(String title) {
        if (title == null || title.isBlank()) {
            throw invalid("Task Step title is required.");
        }
        String normalized = title.trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw invalid("Task Step title must not exceed 255 characters.");
        }
        return normalized;
    }

    public String normalizeOptionalGuidance(String guidance) {
        if (guidance == null || guidance.isBlank()) {
            return null;
        }
        String normalized = guidance.trim();
        if (normalized.length() > MAX_GUIDANCE_LENGTH) {
            throw invalid("Task Step guidance must not exceed 4000 characters.");
        }
        return normalized;
    }

    private void validateText(String title, String guidance) {
        normalizeRequiredTitle(title);
        normalizeOptionalGuidance(guidance);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TASK_STEP_INVALID, message);
    }
}

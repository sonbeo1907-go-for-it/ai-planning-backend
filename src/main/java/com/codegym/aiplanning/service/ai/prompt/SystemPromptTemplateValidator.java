package com.codegym.aiplanning.service.ai.prompt;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SystemPromptTemplateValidator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private static final Map<AiPurpose, Set<String>> ALLOWED_PLACEHOLDERS = Map.of(
            AiPurpose.ROADMAP_GENERATION, Set.of("language"),
            AiPurpose.DAILY_PLAN_GENERATION, Set.of("language", "availableMinutes", "maxReviewMinutes"),
            AiPurpose.QUIZ_GENERATION, Set.of("language"),
            AiPurpose.TASK_GUIDANCE_GENERATION, Set.of("language")
    );

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]{20,}"),
            Pattern.compile("sk-[a-zA-Z0-9_\\-]{20,}"),
            Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"),
            Pattern.compile("(?i)(api[_-]?key|secret|password|auth[_-]?token)\\s*[:=]\\s*['\"]?[a-zA-Z0-9_\\-]{16,}['\"]?"),
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----")
    );

    public void validateContent(AiPurpose purpose, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Prompt template content must not be blank.");
        }

        // Layer 1: Check placeholders against Purpose allowlist
        Set<String> allowed = ALLOWED_PLACEHOLDERS.getOrDefault(purpose, Set.of());
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(content);
        while (placeholderMatcher.find()) {
            String placeholder = placeholderMatcher.group(1);
            if (!allowed.contains(placeholder)) {
                throw new BusinessException(
                        ErrorCode.PROMPT_PLACEHOLDER_INVALID,
                        "Placeholder '{{" + placeholder + "}}' is not supported for purpose " + purpose + ".");
            }
        }

        // Layer 2: Check for potential credentials and secrets
        for (Pattern secretPattern : SECRET_PATTERNS) {
            if (secretPattern.matcher(content).find()) {
                throw new BusinessException(
                        ErrorCode.PROMPT_CONTAINS_SECRET,
                        "Prompt content must not contain credentials, API keys, or private secrets.");
            }
        }
    }

    public String renderPreview(AiPurpose purpose, String template, Map<String, String> syntheticData) {
        validateContent(purpose, template);

        Map<String, String> mergedData = new HashMap<>(getDefaultSyntheticData(purpose));
        if (syntheticData != null) {
            mergedData.putAll(syntheticData);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = mergedData.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> getDefaultSyntheticData(AiPurpose purpose) {
        return Map.of(
                "language", "vi-VN",
                "availableMinutes", "120",
                "maxReviewMinutes", "36"
        );
    }
}

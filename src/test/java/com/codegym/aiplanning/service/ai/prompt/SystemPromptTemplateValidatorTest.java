package com.codegym.aiplanning.service.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemPromptTemplateValidatorTest {

    private SystemPromptTemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SystemPromptTemplateValidator();
    }

    @Test
    @DisplayName("Blank or null content throws VALIDATION_FAILED")
    void blankContent_throwsValidationFailed() {
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, "   "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("Layer 1: Allowed placeholders pass validation")
    void allowedPlaceholders_passValidation() {
        String validDailyPrompt = "Daily plan limit {{availableMinutes}} min, review {{maxReviewMinutes}} min, lang: {{language}}.";
        validator.validateContent(AiPurpose.DAILY_PLAN_GENERATION, validDailyPrompt);

        String validRoadmapPrompt = "Roadmap in {{language}}.";
        validator.validateContent(AiPurpose.ROADMAP_GENERATION, validRoadmapPrompt);
    }

    @Test
    @DisplayName("Layer 1: Disallowed placeholder throws PROMPT_PLACEHOLDER_INVALID")
    void disallowedPlaceholder_throwsPlaceholderInvalid() {
        String invalidPrompt = "Hello {{unsupportedVar}}.";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, invalidPrompt))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_PLACEHOLDER_INVALID));

        String dailyPlanOnlyVarOnRoadmap = "Hello {{availableMinutes}}.";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, dailyPlanOnlyVarOnRoadmap))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_PLACEHOLDER_INVALID));
    }

    @Test
    @DisplayName("Layer 2: Secret scanner rejects Bearer tokens")
    void secretScanner_rejectsBearerToken() {
        String promptWithBearer = "Use this auth: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz123";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, promptWithBearer))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_CONTAINS_SECRET));
    }

    @Test
    @DisplayName("Layer 2: Secret scanner rejects OpenAI and generic API keys")
    void secretScanner_rejectsApiKeys() {
        String promptWithSk = "Provider key: sk-abcdef1234567890abcdef1234567890";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, promptWithSk))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_CONTAINS_SECRET));

        String promptWithGoogleKey = "AIzaSyD-1234567890123456789012345678901";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, promptWithGoogleKey))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_CONTAINS_SECRET));

        String promptWithGenericKey = "api_key = 'abcdef1234567890abcdef'";
        assertThatThrownBy(() -> validator.validateContent(AiPurpose.ROADMAP_GENERATION, promptWithGenericKey))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.PROMPT_CONTAINS_SECRET));
    }

    @Test
    @DisplayName("renderPreview substitutes synthetic values correctly")
    void renderPreview_substitutesVariables() {
        String template = "Max: {{availableMinutes}} min, lang: {{language}}.";
        String rendered = validator.renderPreview(
                AiPurpose.DAILY_PLAN_GENERATION,
                template,
                Map.of("availableMinutes", "90", "language", "en-US"));

        assertThat(rendered).isEqualTo("Max: 90 min, lang: en-US.");
    }

    @Test
    @DisplayName("renderPreview falls back to default synthetic sample data when not supplied")
    void renderPreview_usesDefaultSyntheticData() {
        String template = "Plan lang: {{language}}.";
        String rendered = validator.renderPreview(
                AiPurpose.ROADMAP_GENERATION,
                template,
                null);

        assertThat(rendered).isEqualTo("Plan lang: vi-VN.");
    }
}

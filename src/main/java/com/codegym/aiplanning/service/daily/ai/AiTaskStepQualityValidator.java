package com.codegym.aiplanning.service.daily.ai;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic quality checks for provider-generated step text.
 *
 * <p>This validator deliberately verifies evidence that can be checked without another AI call:
 * an observable action, a source-grounded scope anchor, and sufficient distance from curriculum
 * titles. It does not claim to prove arbitrary natural-language semantics.
 */
public class AiTaskStepQualityValidator {

    private final LearningUnitAnchorMatcher anchorMatcher = new LearningUnitAnchorMatcher();
    private static final double PARAPHRASE_CONTAINMENT_THRESHOLD = 0.80;
    private static final Set<String> ACTION_WORDS = Set.of(
            "write", "implement", "compare", "explain", "solve", "run", "read", "review", "summarize",
            "study", "learn", "use", "viet", "cai", "xay", "so", "giai", "chay", "doc", "on", "tom",
            "hoc", "dung");
    private static final Set<String> CONCRETE_ACTION_WORDS = Set.of(
            "write", "implement", "compare", "explain", "solve", "run", "read", "review", "summarize",
            "viet", "cai", "xay", "so", "giai", "chay", "doc", "tom");
    private static final Set<String> IGNORED_WORDS = Set.of(
            "a", "an", "the", "to", "of", "with", "and", "or", "for", "in", "on", "using",
            "mot", "cac", "cua", "va", "voi", "cho", "trong", "bang", "su", "dung");

    public void validate(
            DailyPlanAiResponse.AiTaskStepDto step,
            String parentTaskTitle,
            String learningUnitTitle,
            String learningUnitDescription,
            String parentTopicTitle) {
        if (step.actionType() == null) {
            throw invalid("step.actionType is required.");
        }
        if (!anchorMatcher.validLength(step.scopeAnchor())) {
            throw invalid(
                    "step.scopeAnchor must be a short, meaningful source phrase.",
                    DailyPlanValidationReason.STEP_ANCHOR_MISSING);
        }

        boolean hasCurriculumContext = !normalize(learningUnitTitle).isBlank()
                || !normalize(learningUnitDescription).isBlank();
        String referenceTitle = hasCurriculumContext ? learningUnitTitle : parentTaskTitle;
        String referenceDescription = hasCurriculumContext ? learningUnitDescription : null;
        if (!anchorMatcher.belongsToLearningUnit(
                step.scopeAnchor(), referenceTitle, referenceDescription)) {
            throw invalid(
                    "A Task Step scope anchor is outside its supplied Learning Unit context.",
                    DailyPlanValidationReason.STEP_ANCHOR_OUTSIDE_UNIT);
        }

        if (!anchorMatcher.usedInStep(
                step.scopeAnchor(), step.title(), step.guidance())) {
            throw invalid(
                    "A Task Step must use its source-grounded scope anchor.",
                    DailyPlanValidationReason.STEP_ANCHOR_UNUSED);
        }

        rejectLikelyParaphrase(
                step.title(), step.guidance(), parentTaskTitle, step.scopeAnchor(), "parent task");
        rejectLikelyParaphrase(
                step.title(), step.guidance(), learningUnitTitle, step.scopeAnchor(), "Learning Unit");
        rejectLikelyParaphrase(
                step.title(), step.guidance(), parentTopicTitle, step.scopeAnchor(), "parent Topic");
    }

    private void rejectLikelyParaphrase(
            String stepTitle,
            String stepGuidance,
            String referenceTitle,
            String scopeAnchor,
            String label) {
        if (normalize(stepTitle).equals(normalize(referenceTitle))) {
            throw invalid("A Task Step must not repeat or merely rename its " + label + ".");
        }
        Set<String> stepTokens = new HashSet<>(meaningfulTokens(stepTitle));
        Set<String> referenceTokens = new HashSet<>(meaningfulTokens(referenceTitle));
        Set<String> anchorTokens = tokens(scopeAnchor);
        stepTokens.removeAll(anchorTokens);
        referenceTokens.removeAll(anchorTokens);
        if (stepTokens.isEmpty() || referenceTokens.isEmpty()) {
            return;
        }
        Set<String> intersection = new HashSet<>(stepTokens);
        intersection.retainAll(referenceTokens);
        double containment = intersection.size()
                / (double) Math.min(stepTokens.size(), referenceTokens.size());
        boolean hasLittleNewInformation =
                stepTokens.size() <= referenceTokens.size() + 1;
        if (containment >= PARAPHRASE_CONTAINMENT_THRESHOLD
                && hasLittleNewInformation
                && !hasConcreteGuidance(stepGuidance, referenceTitle)) {
            throw invalid("A Task Step must not repeat or merely rename its " + label + ".");
        }
    }

    private boolean hasConcreteGuidance(String guidance, String referenceTitle) {
        Set<String> guidanceTokens = tokens(guidance);
        boolean hasAction = guidanceTokens.stream().anyMatch(CONCRETE_ACTION_WORDS::contains);
        Set<String> newDetails = new HashSet<>(meaningfulTokens(guidance));
        newDetails.removeAll(meaningfulTokens(referenceTitle));
        return hasAction && newDetails.size() >= 2;
    }

    private Set<String> meaningfulTokens(String value) {
        return tokens(value).stream()
                .filter(word -> !ACTION_WORDS.contains(word))
                .filter(word -> !IGNORED_WORDS.contains(word))
                .filter(word -> word.length() > 1)
                .collect(Collectors.toSet());
    }

    private Set<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private InvalidAiDailyPlanResponseException invalid(String message) {
        return new InvalidAiDailyPlanResponseException(message);
    }

    private InvalidAiDailyPlanResponseException invalid(
            String message, DailyPlanValidationReason reason) {
        return new InvalidAiDailyPlanResponseException(message, reason);
    }
}

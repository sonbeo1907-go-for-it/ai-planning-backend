package com.codegym.aiplanning.service.daily.ai;

import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Business validator for task-suggestion AI output (US-TSK-AI).
 *
 * <p>The validator enforces the "Gợi ý chưa xác minh" contract:
 * a reference is {@code verified} only when it is a {@code DOCUMENT}
 * reference pointing to one original document supplied in the AI context.
 * External {@code LINK} references are always unverified. URLs are restricted
 * to http/https with no embedded credentials.
 */
@Component
public class TaskSuggestionValidator {

    public ValidatedTaskSuggestion validate(
            TaskSuggestionAiResponse response, TaskSuggestionContext context) {
        if (response == null) {
            throw invalid("The AI suggestion is missing.");
        }
        if (response.shortDescription() == null
                || response.shortDescription().isBlank()
                || response.shortDescription().trim().length() > 4000) {
            throw invalid("shortDescription must be a non-blank string within 4000 characters.");
        }

        if (response.steps() == null
                || response.steps().isEmpty()
                || response.steps().size() > 20) {
            throw invalid("The checklist must contain between 1 and 20 steps.");
        }
        Set<String> stepKeys = new HashSet<>();
        List<String> steps = new ArrayList<>();
        for (TaskSuggestionAiResponse.ChecklistStepDto step : response.steps()) {
            requireText(step.content(), 1000, "step.content");
            String normalized = step.content().trim().toLowerCase(Locale.ROOT);
            if (!stepKeys.add(normalized)) {
                throw invalid("The checklist contains a duplicate step.");
            }
            steps.add(step.content().trim());
        }


        if (response.references() == null || response.references().size() > 10) {
            throw invalid("references must contain at most 10 entries.");
        }
        Set<UUID> sourceIds = context.sources().stream()
                .map(TaskSuggestionContext.SourceDocument::documentId)
                .collect(java.util.stream.Collectors.toSet());
        List<ValidatedTaskSuggestion.ValidatedReference> references = new ArrayList<>();
        for (TaskSuggestionAiResponse.ReferenceDto reference : response.references()) {
            requireText(reference.title(), 255, "reference.title");
            TaskAiReferenceType type = parseType(reference.referenceType());
            if (type == TaskAiReferenceType.DOCUMENT) {
                if (reference.documentId() == null) {
                    throw invalid("A DOCUMENT reference requires a documentId.");
                }
                if (reference.url() != null && !reference.url().isBlank()) {
                    throw invalid("A DOCUMENT reference must not contain a url.");
                }
                if (!sourceIds.contains(reference.documentId())) {
                    throw invalid(
                            "A DOCUMENT reference must point to an original user document supplied in the context.");
                }
                references.add(new ValidatedTaskSuggestion.ValidatedReference(
                        TaskAiReferenceType.DOCUMENT,
                        reference.title().trim(),
                        null,
                        reference.documentId(),
                        true));
            } else {
                requireSafeHttpUrl(reference.url());
                if (reference.documentId() != null) {
                    throw invalid("A LINK reference must not contain a documentId.");
                }
                references.add(new ValidatedTaskSuggestion.ValidatedReference(
                        TaskAiReferenceType.LINK,
                        reference.title().trim(),
                        reference.url().trim(),
                        null,
                        false));
            }
        }

        return new ValidatedTaskSuggestion(response.shortDescription().trim(), steps, references);
    }

    private TaskAiReferenceType parseType(String referenceType) {
        if (referenceType == null) {
            throw invalid("reference.referenceType is required.");
        }
        try {
            return TaskAiReferenceType.valueOf(referenceType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("reference.referenceType must be DOCUMENT or LINK.");
        }
    }

    private void requireSafeHttpUrl(String url) {
        if (url == null || url.isBlank() || url.trim().length() > 2048) {
            throw invalid("reference.url must be a non-blank URL within 2048 characters.");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw invalid("reference.url must use the http or https scheme.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalid("reference.url must contain a host.");
            }
            if (uri.getUserInfo() != null) {
                throw invalid("reference.url must not embed credentials.");
            }
        } catch (URISyntaxException exception) {
            throw invalid("reference.url is not a valid URL.");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw invalid(field + " must be a non-blank string within its length limit.");
        }
    }

    private InvalidTaskSuggestionException invalid(String message) {
        return new InvalidTaskSuggestionException(message);
    }
}

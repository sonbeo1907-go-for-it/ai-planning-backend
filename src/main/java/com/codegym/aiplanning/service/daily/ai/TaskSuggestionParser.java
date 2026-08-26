package com.codegym.aiplanning.service.daily.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Strict JSON schema parser for task-suggestion AI output (US-TSK-AI).
 * Only the exact documented fields are accepted; any unknown or missing field
 * rejects the whole response so it can be retried.
 */
@Component
public class TaskSuggestionParser {

    private final ObjectMapper objectMapper;

    public TaskSuggestionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static final Set<String> ROOT_FIELDS =
            Set.of("shortDescription", "steps", "references");
    private static final Set<String> STEP_FIELDS = Set.of("content");
    private static final Set<String> REFERENCE_FIELDS =
            Set.of("title", "referenceType", "documentId", "url");

    public TaskSuggestionAiResponse parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw invalid("The AI response is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            requireObjectWithExactFields(root, ROOT_FIELDS, "task suggestion");
            requireText(root, "shortDescription", "task suggestion");
            requireArray(root, "steps", STEP_FIELDS, "step");
            requireArray(root, "references", REFERENCE_FIELDS, "reference");
            return objectMapper.treeToValue(root, TaskSuggestionAiResponse.class);
        } catch (JsonProcessingException exception) {
            throw invalid("The AI response is not valid task suggestion JSON.");
        }
    }

    private void requireArray(
            JsonNode root, String field, Set<String> expectedFields, String context) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            throw invalid("task suggestion." + field + " must be an array.");
        }
        for (JsonNode node : array) {
            requireObjectWithExactFields(node, expectedFields, context);
        }
    }

    private void requireObjectWithExactFields(
            JsonNode node, Set<String> expectedFields, String context) {
        if (!(node instanceof ObjectNode objectNode)) {
            throw invalid("Each " + context + " must be a JSON object.");
        }
        Set<String> actualFields = objectNode.properties()
                .stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!actualFields.equals(expectedFields)) {
            throw invalid("The " + context + " fields do not match the required schema.");
        }
    }

    private void requireText(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + "." + field + " must be a non-blank string.");
        }
    }

    private InvalidTaskSuggestionException invalid(String message) {
        return new InvalidTaskSuggestionException(message);
    }
}

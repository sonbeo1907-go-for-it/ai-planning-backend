package com.codegym.aiplanning.service.daily.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiPlanParser {
    private final ObjectMapper objectMapper;

    public AiPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static final Set<String> ROOT_FIELDS =
            Set.of("summary", "items", "adjustments");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "roadmapItemId",
            "title",
            "description",
            "category",
            "plannedMinutes",
            "aiAdjustmentAction",
            "aiAdjustmentReason");
    private static final Set<String> ADJUSTMENT_FIELDS = Set.of(
            "sourceDailyPlanItemId",
            "title",
            "action",
            "reason",
            "proposedMinutes");

    public DailyPlanAiResponse parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw invalid("The AI response is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            requireObjectWithExactFields(root, ROOT_FIELDS, "daily plan");
            requireText(root, "summary", "daily plan");
            requireArray(root, "items", ITEM_FIELDS, "item");
            requireArray(root, "adjustments", ADJUSTMENT_FIELDS, "adjustment");
            return objectMapper.treeToValue(root, DailyPlanAiResponse.class);
        } catch (JsonProcessingException exception) {
            throw invalid("The AI response is not valid Daily Plan JSON.");
        }
    }

    private void requireArray(
            JsonNode root,
            String field,
            Set<String> expectedFields,
            String context) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray()) {
            throw invalid("daily plan." + field + " must be an array.");
        }
        for (JsonNode node : array) {
            requireObjectWithExactFields(node, expectedFields, context);
        }
    }

    private void requireObjectWithExactFields(
            JsonNode node,
            Set<String> expectedFields,
            String context) {
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

    private InvalidAiDailyPlanResponseException invalid(String message) {
        return new InvalidAiDailyPlanResponseException(message);
    }
}

package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedMilestone;
import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan.GeneratedTopic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiRoadmapSchemaValidator {

    private static final Set<String> ROOT_FIELDS =
            Set.of("title", "description", "milestones");
    private static final Set<String> MILESTONE_FIELDS =
            Set.of("title", "description", "orderIndex", "topics");
    private static final Set<String> TOPIC_FIELDS =
            Set.of("title", "description", "orderIndex", "estimatedMinutes");

    private final ObjectMapper objectMapper;

    public AiRoadmapSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedRoadmapPlan validate(String rawResponse) {
        JsonNode root = parseObject(rawResponse);
        requireExactFields(root, ROOT_FIELDS, "roadmap");

        String title = requireText(root, "title", 200, "roadmap");
        String description = requireText(root, "description", 4000, "roadmap");
        JsonNode milestonesNode = requireArray(root, "milestones", "roadmap");
        requireSize(milestonesNode, 3, 6, "milestones");

        List<GeneratedMilestone> milestones = new ArrayList<>();
        for (int milestoneIndex = 0; milestoneIndex < milestonesNode.size(); milestoneIndex++) {
            JsonNode milestoneNode = milestonesNode.get(milestoneIndex);
            requireExactFields(milestoneNode, MILESTONE_FIELDS, "milestone");
            requireOrderIndex(milestoneNode, milestoneIndex, "milestone");

            JsonNode topicsNode = requireArray(milestoneNode, "topics", "milestone");
            requireSize(topicsNode, 2, 5, "topics");
            List<GeneratedTopic> topics = new ArrayList<>();
            for (int topicIndex = 0; topicIndex < topicsNode.size(); topicIndex++) {
                JsonNode topicNode = topicsNode.get(topicIndex);
                requireExactFields(topicNode, TOPIC_FIELDS, "topic");
                requireOrderIndex(topicNode, topicIndex, "topic");
                int estimatedMinutes = requireInteger(
                        topicNode, "estimatedMinutes", 1, 1440, "topic");
                topics.add(new GeneratedTopic(
                        requireText(topicNode, "title", 200, "topic"),
                        requireText(topicNode, "description", 4000, "topic"),
                        topicIndex,
                        estimatedMinutes));
            }

            milestones.add(new GeneratedMilestone(
                    requireText(milestoneNode, "title", 200, "milestone"),
                    requireText(milestoneNode, "description", 4000, "milestone"),
                    milestoneIndex,
                    List.copyOf(topics)));
        }

        return new GeneratedRoadmapPlan(title, description, List.copyOf(milestones));
    }

    private JsonNode parseObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw invalid("The AI response is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            if (root == null || !root.isObject()) {
                throw invalid("The AI response root must be a JSON object.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw invalid("The AI response is not valid JSON.");
        }
    }

    private void requireExactFields(JsonNode node, Set<String> expected, String context) {
        if (!(node instanceof ObjectNode objectNode)) {
            throw invalid("Each " + context + " must be a JSON object.");
        }
        Set<String> actual = objectNode.properties()
                .stream()
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)) {
            throw invalid("The " + context + " fields do not match the required schema.");
        }
    }

    private String requireText(
            JsonNode node, String field, int maxLength, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + "." + field + " must be a non-blank string.");
        }
        String normalized = value.textValue().trim();
        if (normalized.length() > maxLength) {
            throw invalid(context + "." + field + " exceeds its maximum length.");
        }
        return normalized;
    }

    private JsonNode requireArray(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw invalid(context + "." + field + " must be an array.");
        }
        return value;
    }

    private void requireSize(JsonNode array, int minimum, int maximum, String field) {
        if (array.size() < minimum || array.size() > maximum) {
            throw invalid(field + " must contain between " + minimum + " and " + maximum + " items.");
        }
    }

    private void requireOrderIndex(JsonNode node, int expected, String context) {
        int actual = requireInteger(node, "orderIndex", 0, Integer.MAX_VALUE, context);
        if (actual != expected) {
            throw invalid(context + ".orderIndex must be contiguous and zero-based.");
        }
    }

    private int requireInteger(
            JsonNode node,
            String field,
            int minimum,
            int maximum,
            String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(context + "." + field + " must be an integer.");
        }
        int number = value.intValue();
        if (number < minimum || number > maximum) {
            throw invalid(context + "." + field + " is outside the allowed range.");
        }
        return number;
    }

    private InvalidAiRoadmapResponseException invalid(String message) {
        return new InvalidAiRoadmapResponseException(message);
    }
}

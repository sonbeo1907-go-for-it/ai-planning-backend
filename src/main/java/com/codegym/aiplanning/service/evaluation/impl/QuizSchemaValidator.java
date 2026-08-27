package com.codegym.aiplanning.service.evaluation.impl;

import com.codegym.aiplanning.controller.evaluation.dto.QuizOptionDto;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuestion;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class QuizSchemaValidator {

    private static final Set<String> VALID_OPTION_KEYS = Set.of("A", "B", "C", "D");
    private final ObjectMapper objectMapper;

    public QuizSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedQuizPlan validate(String rawResponse, Set<UUID> validTopicIds) {
        JsonNode root = parseObject(rawResponse);
        JsonNode questionsNode = requireArray(root, "questions", "quiz");
        requireSize(questionsNode, 3, 5, "questions");

        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionsNode.size(); i++) {
            JsonNode qNode = questionsNode.get(i);
            if (!qNode.isObject()) {
                throw invalid("Each question item must be a JSON object.");
            }

            UUID topicId = extractAndValidateTopicId(qNode, validTopicIds);
            String questionText = requireText(qNode, "questionText", 1000, "question[" + i + "]");
            JsonNode optionsNode = requireArray(qNode, "options", "question[" + i + "]");
            if (optionsNode.size() != 4) {
                throw invalid("question[" + i + "].options must have exactly 4 choices.");
            }

            List<QuizOptionDto> options = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                JsonNode optNode = optionsNode.get(j);
                if (!optNode.isObject()) {
                    throw invalid("question[" + i + "].options[" + j + "] must be a JSON object.");
                }
                String key = requireText(optNode, "key", 10, "option.key").toUpperCase();
                if (!VALID_OPTION_KEYS.contains(key)) {
                    throw invalid("Option key must be A, B, C, or D.");
                }
                String text = requireText(optNode, "text", 500, "option.text");
                options.add(new QuizOptionDto(key, text));
            }

            String correctOption = requireText(qNode, "correctOption", 10, "question[" + i + "]").toUpperCase();
            if (!VALID_OPTION_KEYS.contains(correctOption)) {
                throw invalid("question[" + i + "].correctOption must be A, B, C, or D.");
            }

            String explanation = requireText(qNode, "explanation", 2000, "question[" + i + "]");

            questions.add(new GeneratedQuestion(
                    topicId,
                    questionText,
                    List.copyOf(options),
                    correctOption,
                    explanation,
                    i));
        }

        return new GeneratedQuizPlan(List.copyOf(questions));
    }

    private UUID extractAndValidateTopicId(JsonNode qNode, Set<UUID> validTopicIds) {
        JsonNode topicIdNode = qNode.get("topicId");
        if (topicIdNode == null || !topicIdNode.isTextual() || topicIdNode.textValue().isBlank()) {
            // If topicId is omitted or blank, and only 1 validTopicId exists, fallback to it
            if (validTopicIds != null && validTopicIds.size() == 1) {
                return validTopicIds.iterator().next();
            }
            throw invalid("Question topicId must be a non-blank UUID string.");
        }

        try {
            UUID topicId = UUID.fromString(topicIdNode.textValue().trim());
            if (validTopicIds != null && !validTopicIds.isEmpty() && !validTopicIds.contains(topicId)) {
                // If AI picked a topicId not in allowed list, fallback to one of validTopicIds if available
                if (validTopicIds.size() == 1) {
                    return validTopicIds.iterator().next();
                }
                throw invalid("Question topicId " + topicId + " is not in the list of completed topics.");
            }
            return topicId;
        } catch (IllegalArgumentException e) {
            throw invalid("Question topicId is not a valid UUID format.");
        }
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

    private String requireText(JsonNode node, String field, int maxLength, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(context + "." + field + " must be a non-blank string.");
        }
        String normalized = value.textValue().trim();
        if (normalized.length() > maxLength) {
            throw invalid(context + "." + field + " exceeds its maximum length of " + maxLength + ".");
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

    private InvalidAiQuizResponseException invalid(String message) {
        return new InvalidAiQuizResponseException(message);
    }
}

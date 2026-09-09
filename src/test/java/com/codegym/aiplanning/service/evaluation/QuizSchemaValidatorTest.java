package com.codegym.aiplanning.service.evaluation;

import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.codegym.aiplanning.service.evaluation.impl.InvalidAiQuizResponseException;
import com.codegym.aiplanning.service.evaluation.impl.QuizSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizSchemaValidatorTest {

    private QuizSchemaValidator validator;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        validator = new QuizSchemaValidator(new ObjectMapper());
        topicId = UUID.randomUUID();
    }

    @Test
    void shouldValidateCorrectQuizJson() {
        String json = """
                {
                  "questions": [
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 1 là gì?",
                      "options": [
                        { "key": "A", "text": "Đáp án A" },
                        { "key": "B", "text": "Đáp án B" },
                        { "key": "C", "text": "Đáp án C" },
                        { "key": "D", "text": "Đáp án D" }
                      ],
                      "correctOption": "A",
                      "explanation": "Giải thích câu 1 đúng"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 2 là gì?",
                      "options": [
                        { "key": "A", "text": "Đáp án A" },
                        { "key": "B", "text": "Đáp án B" },
                        { "key": "C", "text": "Đáp án C" },
                        { "key": "D", "text": "Đáp án D" }
                      ],
                      "correctOption": "B",
                      "explanation": "Giải thích câu 2 đúng"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 3 là gì?",
                      "options": [
                        { "key": "A", "text": "Đáp án A" },
                        { "key": "B", "text": "Đáp án B" },
                        { "key": "C", "text": "Đáp án C" },
                        { "key": "D", "text": "Đáp án D" }
                      ],
                      "correctOption": "C",
                      "explanation": "Giải thích câu 3 đúng"
                    }
                  ]
                }
                """.formatted(topicId, topicId, topicId);

        GeneratedQuizPlan result = validator.validate(json, Set.of(topicId));

        assertThat(result).isNotNull();
        assertThat(result.questions()).hasSize(3);
        assertThat(result.questions().get(0).questionText()).isEqualTo("Câu hỏi 1 là gì?");
        assertThat(result.questions().get(0).correctOption()).isEqualTo("A");
        assertThat(result.questions().get(0).options()).hasSize(4);
    }

    @Test
    void shouldRejectWhenLessThan3Questions() {
        String json = """
                {
                  "questions": [
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 1",
                      "options": [
                        { "key": "A", "text": "A" }, { "key": "B", "text": "B" },
                        { "key": "C", "text": "C" }, { "key": "D", "text": "D" }
                      ],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    }
                  ]
                }
                """.formatted(topicId);

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("questions must contain between 3 and 5 items");
    }

    @Test
    void shouldRejectWhenOptionsCountNot4() {
        String json = """
                {
                  "questions": [
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 1",
                      "options": [{ "key": "A", "text": "A" }, { "key": "B", "text": "B" }],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 2",
                      "options": [{ "key": "A", "text": "A" }, { "key": "B", "text": "B" }, { "key": "C", "text": "C" }, { "key": "D", "text": "D" }],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 3",
                      "options": [{ "key": "A", "text": "A" }, { "key": "B", "text": "B" }, { "key": "C", "text": "C" }, { "key": "D", "text": "D" }],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    }
                  ]
                }
                """.formatted(topicId, topicId, topicId);

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("options must have exactly 4 choices");
    }

    @Test
    void shouldRejectWhenInvalidCorrectOption() {
        String json = """
                {
                  "questions": [
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 1",
                      "options": [
                        { "key": "A", "text": "A" }, { "key": "B", "text": "B" },
                        { "key": "C", "text": "C" }, { "key": "D", "text": "D" }
                      ],
                      "correctOption": "E",
                      "explanation": "Giải thích"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 2",
                      "options": [
                        { "key": "A", "text": "A" }, { "key": "B", "text": "B" },
                        { "key": "C", "text": "C" }, { "key": "D", "text": "D" }
                      ],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    },
                    {
                      "topicId": "%s",
                      "questionText": "Câu hỏi 3",
                      "options": [
                        { "key": "A", "text": "A" }, { "key": "B", "text": "B" },
                        { "key": "C", "text": "C" }, { "key": "D", "text": "D" }
                      ],
                      "correctOption": "A",
                      "explanation": "Giải thích"
                    }
                  ]
                }
                """.formatted(topicId, topicId, topicId);

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("correctOption must be A, B, C, or D");
    }

    @Test
    void shouldRejectMissingTopicIdEvenWhenOnlyOneTopicIsAllowed() {
        String json = validThreeQuestionJson("")
                .replace("\"topicId\": \"\",", "");

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("topicId must be a non-blank UUID string");
    }

    @Test
    void shouldRejectTopicOutsideTheAllowedContext() {
        String json = validThreeQuestionJson(UUID.randomUUID().toString());

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("is not in the list of completed Learning Units");
    }

    @Test
    void shouldRejectDuplicateOptionKeys() {
        String json = validThreeQuestionJson(topicId.toString())
                .replace("{ \"key\": \"D\", \"text\": \"D\" }",
                        "{ \"key\": \"C\", \"text\": \"D\" }");

        assertThatThrownBy(() -> validator.validate(json, Set.of(topicId)))
                .isInstanceOf(InvalidAiQuizResponseException.class)
                .hasMessageContaining("exactly once");
    }

    private String validThreeQuestionJson(String questionTopicId) {
        String question = """
                {
                  "topicId": "%s",
                  "questionText": "Question",
                  "options": [
                    { "key": "A", "text": "A" },
                    { "key": "B", "text": "B" },
                    { "key": "C", "text": "C" },
                    { "key": "D", "text": "D" }
                  ],
                  "correctOption": "A",
                  "explanation": "Explanation"
                }
                """.formatted(questionTopicId);
        return "{\"questions\":[%s,%s,%s]}".formatted(
                question,
                question,
                question);
    }
}

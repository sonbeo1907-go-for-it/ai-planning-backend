package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskSuggestionParserTest {

    private TaskSuggestionParser parser;

    @BeforeEach
    void setUp() {
        parser = new TaskSuggestionParser(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void parse_acceptsValidSuggestion() {
        String documentId = UUID.randomUUID().toString();
        String json = """
                {
                  "shortDescription": "Đọc tài liệu rồi làm bài tập thực hành.",
                  "steps": [
                    {"content": "Mở tài liệu Spring"},
                    {"content": "Chạy ví dụ CRUD"}
                  ],
                  "references": [
                    {"title": "Tài liệu Spring", "referenceType": "DOCUMENT", "documentId": "%s", "url": null},
                    {"title": "Baeldung", "referenceType": "LINK", "documentId": null, "url": "https://baeldung.com/spring"}
                  ]
                }
                """.formatted(documentId);

        TaskSuggestionAiResponse response = parser.parse(json);

        assertThat(response.shortDescription()).contains("tài liệu");
        assertThat(response.steps()).hasSize(2);
        assertThat(response.references()).hasSize(2);
    }

    @Test
    void parse_rejectsEmptyBody() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parse_rejectsNonJsonBody() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("not valid task suggestion JSON");
    }

    @Test
    void parse_rejectsMissingRootField() {
        String json = """
                {
                  "shortDescription": "x",
                  "steps": [{"content": "a"}]
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("fields do not match");
    }

    @Test
    void parse_rejectsUnknownFieldInStep() {
        String json = """
                {
                  "shortDescription": "x",
                  "steps": [{"content": "a", "extra": true}],
                  "references": []
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("step fields do not match");
    }

    @Test
    void parse_rejectsBlankShortDescription() {
        String json = """
                {
                  "shortDescription": " ",
                  "steps": [{"content": "a"}],
                  "references": []
                }
                """;
        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("non-blank string");
    }
}

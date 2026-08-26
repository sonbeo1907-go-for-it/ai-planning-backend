package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskSuggestionValidatorTest {

    private TaskSuggestionValidator validator;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        validator = new TaskSuggestionValidator();
        documentId = UUID.randomUUID();
    }

    @Test
    void validate_marksOriginalDocumentAsVerified() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "Học Spring cơ bản",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("Đọc lý thuyết")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Tài liệu Spring", "DOCUMENT", documentId, null)));

        ValidatedTaskSuggestion validated = validator.validate(response, context());

        assertThat(validated.shortDescription()).isEqualTo("Học Spring cơ bản");
        assertThat(validated.steps()).containsExactly("Đọc lý thuyết");
        assertThat(validated.references()).hasSize(1);
        assertThat(validated.references().get(0).type()).isEqualTo(TaskAiReferenceType.DOCUMENT);
        assertThat(validated.references().get(0).documentId()).isEqualTo(documentId);
        assertThat(validated.references().get(0).verified()).isTrue();
    }

    @Test
    void validate_marksExternalLinkAsUnverified() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "Làm bài tập",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("Viết CRUD")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Baeldung", "LINK", null, "https://baeldung.com/spring-boot")));

        ValidatedTaskSuggestion validated = validator.validate(response, context());

        assertThat(validated.references()).hasSize(1);
        assertThat(validated.references().get(0).type()).isEqualTo(TaskAiReferenceType.LINK);
        assertThat(validated.references().get(0).url()).isEqualTo("https://baeldung.com/spring-boot");
        assertThat(validated.references().get(0).verified()).isFalse();
    }

    @Test
    void validate_rejectsDocumentNotInContext() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("a")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Tài liệu lạ", "DOCUMENT", UUID.randomUUID(), null)));

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("original user document");
    }

    @Test
    void validate_rejectsUnsafeUrlScheme() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("a")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Evil", "LINK", null, "javascript:alert(1)")));

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void validate_rejectsUrlWithEmbeddedCredentials() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("a")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Leak", "LINK", null, "https://user:pass@example.com")));

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("credentials");
    }

    @Test
    void validate_rejectsEmptyChecklist() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x", List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("between 1 and 20 steps");
    }

    @Test
    void validate_rejectsTooManyReferences() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("a")),
                java.util.stream.IntStream.range(0, 11)
                        .mapToObj(i -> new TaskSuggestionAiResponse.ReferenceDto(
                                "Link " + i, "LINK", null,
                                "https://example.com/" + i))
                        .toList());

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("at most 10");
    }

    @Test
    void validate_rejectsDocumentWithUrl() {
        TaskSuggestionAiResponse response = new TaskSuggestionAiResponse(
                "x",
                List.of(new TaskSuggestionAiResponse.ChecklistStepDto("a")),
                List.of(new TaskSuggestionAiResponse.ReferenceDto(
                        "Tài liệu", "DOCUMENT", documentId, "https://example.com")));

        assertThatThrownBy(() -> validator.validate(response, context()))
                .isInstanceOf(InvalidTaskSuggestionException.class)
                .hasMessageContaining("must not contain a url");
    }

    private TaskSuggestionContext context() {
        return new TaskSuggestionContext(
                UUID.randomUUID(),
                "Học Spring",
                "Mô tả",
                DailyTaskCategory.NEW_MATERIAL,
                30,
                null,
                "Thành thạo Spring Boot",
                List.of(new TaskSuggestionContext.SourceDocument(
                        documentId, "MATERIAL", "spring.txt", "Nội dung tài liệu Spring")));
    }
}


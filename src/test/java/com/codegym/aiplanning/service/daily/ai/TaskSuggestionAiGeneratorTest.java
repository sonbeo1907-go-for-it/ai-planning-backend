package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskSuggestionAiGeneratorTest {

    @Mock
    private AiClientService aiClientService;

    private TaskSuggestionAiGenerator generator;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        generator = new TaskSuggestionAiGenerator(
                aiClientService,
                new TaskSuggestionParser(objectMapper),
                new TaskSuggestionValidator(),
                objectMapper);
        documentId = UUID.randomUUID();
    }

    @Test
    void generate_usesDailyPlanReviewPurposeAndPassesUntrustedContext() {
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString()))
                .thenReturn(validJson());

        ValidatedTaskSuggestion result = generator.generate(context());

        assertThat(result.shortDescription()).contains("Spring");
        assertThat(result.references()).hasSize(1);
        assertThat(result.references().get(0).type()).isEqualTo(TaskAiReferenceType.DOCUMENT);
        assertThat(result.references().get(0).verified()).isTrue();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).generateContent(
                eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("BEGIN_UNTRUSTED_PERSONAL_LEARNING_CONTEXT")
                .contains(documentId.toString());
    }

    @Test
    void generate_retriesInvalidResponseThenAcceptsValidOne() {
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString()))
                .thenReturn("{\"steps\":[]}")
                .thenReturn(validJson());

        ValidatedTaskSuggestion result = generator.generate(context());

        assertThat(result.steps()).hasSize(1);
        verify(aiClientService, times(2)).generateContent(
                eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString());
    }

    @Test
    void generate_rejectsAfterThreeInvalidResponses() {
        when(aiClientService.generateContent(
                        eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString()))
                .thenReturn("{\"shortDescription\":\"x\",\"steps\":[{\"content\":\"a\",\"bad\":1}],\"references\":[]}");

        assertThatThrownBy(() -> generator.generate(context()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_OUTPUT_INVALID);

        verify(aiClientService, times(3)).generateContent(
                eq(AiPurpose.DAILY_PLAN_REVIEW), anyString(), anyString());
    }

    private TaskSuggestionContext context() {
        return new TaskSuggestionContext(
                UUID.randomUUID(),
                "Học REST Controller",
                "Đọc và code thử REST",
                DailyTaskCategory.NEW_MATERIAL,
                30,
                new TaskSuggestionContext.RoadmapTopicContext(
                        UUID.randomUUID(), "Tuần 1", "REST Controller", null),
                "Thành thạo Spring Boot",
                List.of(new TaskSuggestionContext.SourceDocument(
                        documentId, "MATERIAL", "spring.txt", "Nội dung tài liệu Spring")));
    }

    private String validJson() {
        return """
                {
                  "shortDescription": "Đọc tài liệu rồi thực hành Spring.",
                  "steps": [
                    {"content": "Đọc tài liệu"}
                  ],
                  "references": [
                    {"title": "Tài liệu Spring", "referenceType": "DOCUMENT", "documentId": "%s", "url": null}
                  ]
                }
                """.formatted(documentId);
    }
}

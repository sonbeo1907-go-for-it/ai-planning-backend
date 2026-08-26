package com.codegym.aiplanning.service.daily.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemAiSuggestionResponse;
import com.codegym.aiplanning.controller.daily.dto.DailyPlanItemDetailResponse;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.daily.DailyPlan;
import com.codegym.aiplanning.entity.daily.DailyPlanItem;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestion;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionReference;
import com.codegym.aiplanning.entity.daily.DailyPlanItemAiSuggestionStep;
import com.codegym.aiplanning.entity.daily.DailyPlanVersion;
import com.codegym.aiplanning.entity.daily.DailyPlanVersionOrigin;
import com.codegym.aiplanning.entity.daily.DailyTaskCategory;
import com.codegym.aiplanning.entity.daily.TaskAiReferenceType;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionReferenceRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemAiSuggestionStepRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanItemRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanRepository;
import com.codegym.aiplanning.repository.daily.DailyPlanVersionRepository;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionAiGenerator;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionContext;
import com.codegym.aiplanning.service.daily.ai.TaskSuggestionContextBuilder;
import com.codegym.aiplanning.service.daily.ai.ValidatedTaskSuggestion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskAiSuggestionServiceImplTest {

    @Mock
    private DailyPlanRepository dailyPlanRepository;
    @Mock
    private DailyPlanVersionRepository dailyPlanVersionRepository;
    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;
    @Mock
    private DailyPlanItemAiSuggestionRepository suggestionRepository;
    @Mock
    private DailyPlanItemAiSuggestionStepRepository stepRepository;
    @Mock
    private DailyPlanItemAiSuggestionReferenceRepository referenceRepository;
    @Mock
    private TaskSuggestionContextBuilder contextBuilder;
    @Mock
    private TaskSuggestionAiGenerator aiGenerator;
    @Mock
    private AuditLogService auditLogService;

    private TaskAiSuggestionServiceImpl service;

    private UUID userId;
    private UUID planId;
    private UUID versionId;
    private UUID itemId;
    private Jwt userJwt;
    private DailyPlan plan;
    private DailyPlanItem item;

    @BeforeEach
    void setUp() {
        service = new TaskAiSuggestionServiceImpl(
                dailyPlanRepository,
                dailyPlanVersionRepository,
                dailyPlanItemRepository,
                suggestionRepository,
                stepRepository,
                referenceRepository,
                contextBuilder,
                aiGenerator,
                auditLogService);

        userId = UUID.randomUUID();
        planId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        userJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", userId.toString())
                .claim("preferred_username", "testuser")
                .build();
        plan = DailyPlan.create(userId, LocalDate.now(), "Asia/Ho_Chi_Minh", UUID.randomUUID());
        item = DailyPlanItem.create(
                versionId,
                DailyTaskCategory.NEW_MATERIAL,
                "Học Spring",
                "Đọc tài liệu",
                30,
                0,
                UUID.randomUUID());
        ReflectionTestUtils.setField(item, "id", itemId);
    }

    private DailyPlanVersion version() {
        return DailyPlanVersion.create(
                planId, 1, DailyPlanVersionOrigin.MANUAL, 60, 30);
    }

    @Test
    void getTaskDetail_returnsItemWithoutSuggestion() {
        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version()));
        when(suggestionRepository.findByDailyPlanItemId(itemId)).thenReturn(Optional.empty());

        DailyPlanItemDetailResponse detail = service.getTaskDetail(planId, itemId, userJwt);

        assertThat(detail.id()).isEqualTo(itemId);
        assertThat(detail.aiSuggestion()).isNull();
    }

    @Test
    void getTaskDetail_returnsExistingSuggestion() {
        UUID suggestionId = UUID.randomUUID();
        DailyPlanItemAiSuggestion suggestion =
                DailyPlanItemAiSuggestion.create(itemId, "Làm từng bước", "key-1");
        ReflectionTestUtils.setField(suggestion, "id", suggestionId);
        DailyPlanItemAiSuggestionStep step = DailyPlanItemAiSuggestionStep.create(suggestionId, 0, "Bước 1");
        DailyPlanItemAiSuggestionReference reference = DailyPlanItemAiSuggestionReference.create(
                suggestionId, TaskAiReferenceType.LINK, "Baeldung", "https://baeldung.com", null, false);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version()));
        when(suggestionRepository.findByDailyPlanItemId(itemId)).thenReturn(Optional.of(suggestion));
        when(stepRepository.findBySuggestionIdOrderByOrderIndexAsc(suggestionId)).thenReturn(List.of(step));
        when(referenceRepository.findBySuggestionId(suggestionId)).thenReturn(List.of(reference));

        DailyPlanItemDetailResponse detail = service.getTaskDetail(planId, itemId, userJwt);

        assertThat(detail.aiSuggestion()).isNotNull();
        assertThat(detail.aiSuggestion().steps()).hasSize(1);
        assertThat(detail.aiSuggestion().references()).hasSize(1);
        assertThat(detail.aiSuggestion().references().get(0).verified()).isFalse();
    }

    @Test
    void getTaskDetail_rejectsForeignPlanOwner() {
        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTaskDetail(planId, itemId, userJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getTaskDetail_rejectsItemOutsidePlan() {
        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTaskDetail(planId, itemId, userJwt))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND);
    }


    @Test
    void generateSuggestion_createsAndPersistsSuggestion() {
        UUID suggestionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        TaskSuggestionContext context = new TaskSuggestionContext(
                itemId, "Học Spring", null, DailyTaskCategory.NEW_MATERIAL, 30, null, "goal",
                List.of(new TaskSuggestionContext.SourceDocument(
                        documentId, "MATERIAL", "spring.txt", "content")));
        ValidatedTaskSuggestion validated = new ValidatedTaskSuggestion(
                "Làm từng bước",
                List.of("Bước 1", "Bước 2"),
                List.of(new ValidatedTaskSuggestion.ValidatedReference(
                        TaskAiReferenceType.DOCUMENT, "Tài liệu", null, documentId, true)));
        DailyPlanItemAiSuggestion created =
                DailyPlanItemAiSuggestion.create(itemId, "Làm từng bước", "key-1");
        ReflectionTestUtils.setField(created, "id", suggestionId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version()));
        when(suggestionRepository.findByDailyPlanItemId(itemId))
                .thenReturn(Optional.empty(), Optional.of(created));
        when(contextBuilder.build(plan, item)).thenReturn(context);
        when(aiGenerator.generate(context)).thenReturn(validated);
        when(suggestionRepository.saveAndFlush(any(DailyPlanItemAiSuggestion.class)))
                .thenReturn(created);
        when(stepRepository.findBySuggestionIdOrderByOrderIndexAsc(suggestionId)).thenReturn(List.of());
        when(referenceRepository.findBySuggestionId(suggestionId)).thenReturn(List.of());

        DailyPlanItemAiSuggestionResponse response =
                service.generateSuggestion(planId, itemId, "key-1", userJwt);

        assertThat(response.shortDescription()).isEqualTo("Làm từng bước");
        verify(stepRepository).saveAll(anyList());
        verify(referenceRepository).saveAll(anyList());
        verify(auditLogService).logAction(
                eq(userId), eq("testuser"), eq(AuditEventAction.TASK_AI_SUGGESTION_GENERATED),
                eq("DailyPlanItem"), eq(itemId.toString()));
    }

    @Test
    void generateSuggestion_isIdempotentWhenSuggestionExists() {
        UUID suggestionId = UUID.randomUUID();
        DailyPlanItemAiSuggestion existing =
                DailyPlanItemAiSuggestion.create(itemId, "Đã có", "old-key");
        ReflectionTestUtils.setField(existing, "id", suggestionId);

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version()));
        when(suggestionRepository.findByDailyPlanItemId(itemId)).thenReturn(Optional.of(existing));
        when(stepRepository.findBySuggestionIdOrderByOrderIndexAsc(suggestionId)).thenReturn(List.of());
        when(referenceRepository.findBySuggestionId(suggestionId)).thenReturn(List.of());

        DailyPlanItemAiSuggestionResponse response =
                service.generateSuggestion(planId, itemId, "new-key", userJwt);

        assertThat(response.shortDescription()).isEqualTo("Đã có");
        verify(aiGenerator, never()).generate(any());
    }

    @Test
    void regenerateSuggestion_replacesExistingContent() {
        UUID suggestionId = UUID.randomUUID();
        DailyPlanItemAiSuggestion existing =
                DailyPlanItemAiSuggestion.create(itemId, "Cũ", "old-key");
        ReflectionTestUtils.setField(existing, "id", suggestionId);
        ValidatedTaskSuggestion validated = new ValidatedTaskSuggestion(
                "Mới", List.of("Bước mới"), List.of());

        when(dailyPlanRepository.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(dailyPlanItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(dailyPlanVersionRepository.findByIdAndDailyPlanId(versionId, planId))
                .thenReturn(Optional.of(version()));
        when(suggestionRepository.findByDailyPlanItemId(itemId)).thenReturn(Optional.of(existing));
        when(contextBuilder.build(plan, item))
                .thenReturn(new TaskSuggestionContext(
                        itemId, "Học Spring", null, DailyTaskCategory.NEW_MATERIAL, 30,
                        null, "goal", List.of()));
        when(aiGenerator.generate(any(TaskSuggestionContext.class))).thenReturn(validated);
        when(suggestionRepository.saveAndFlush(any(DailyPlanItemAiSuggestion.class)))
                .thenReturn(existing);
        when(stepRepository.findBySuggestionIdOrderByOrderIndexAsc(suggestionId)).thenReturn(List.of());
        when(referenceRepository.findBySuggestionId(suggestionId)).thenReturn(List.of());

        DailyPlanItemAiSuggestionResponse response =
                service.regenerateSuggestion(planId, itemId, userJwt);

        assertThat(response.shortDescription()).isEqualTo("Mới");
        verify(stepRepository).deleteBySuggestionId(suggestionId);
        verify(referenceRepository).deleteBySuggestionId(suggestionId);
        verify(stepRepository).saveAll(anyList());
        verify(auditLogService).logAction(
                eq(userId), eq("testuser"), eq(AuditEventAction.TASK_AI_SUGGESTION_REGENERATED),
                eq("DailyPlanItem"), eq(itemId.toString()));
    }
}


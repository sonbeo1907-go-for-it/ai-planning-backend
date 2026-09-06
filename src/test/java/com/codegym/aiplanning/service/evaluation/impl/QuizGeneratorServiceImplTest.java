package com.codegym.aiplanning.service.evaluation.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.roadmap.RoadmapItem;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizGeneratorServiceImplTest {

    @Mock
    private QuizAiGenerator quizAiGenerator;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapItem roadmapItem;

    private QuizGeneratorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuizGeneratorServiceImpl(
                quizAiGenerator,
                roadmapItemRepository);
    }

    @Test
    void dailyQuizRejectsAnyTopicOutsideTheOwnerScope() {
        UUID userId = UUID.randomUUID();
        UUID ownedTopicId = UUID.randomUUID();
        UUID foreignTopicId = UUID.randomUUID();
        when(roadmapItemRepository.findAllOwnedByIds(
                        List.of(ownedTopicId, foreignTopicId),
                        userId))
                .thenReturn(List.of(roadmapItem));

        assertThatThrownBy(() -> service.generateDailyQuizQuestions(
                        userId,
                        UUID.randomUUID(),
                        List.of(ownedTopicId, foreignTopicId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(quizAiGenerator, never())
                .generateDailyQuiz(org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void masteryQuizRejectsAQuestionTopicOutsideTheOwnerScope() {
        UUID userId = UUID.randomUUID();
        UUID roadmapItemId = UUID.randomUUID();
        when(roadmapItemRepository.findOwnedById(roadmapItemId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateMasteryCheckQuestions(
                        userId,
                        UUID.randomUUID(),
                        roadmapItemId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(quizAiGenerator, never())
                .generateMasteryCheck(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}

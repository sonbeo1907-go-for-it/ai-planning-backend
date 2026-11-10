package com.codegym.aiplanning.service.evaluation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuestion;
import com.codegym.aiplanning.service.evaluation.QuizGeneratorService.GeneratedQuizPlan;
import com.codegym.aiplanning.service.evaluation.impl.QuizAiGenerator.CompletedLearningUnitInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MasteryQuizGenerationTest {

    @Test
    void retriesAThreeQuestionResponseUntilTheMasteryCheckHasFive() {
        AiClientService aiClient = mock(AiClientService.class);
        QuizSchemaValidator validator = mock(QuizSchemaValidator.class);
        QuizAiGenerator generator = new QuizAiGenerator(aiClient, validator, new ObjectMapper());
        UUID learningUnitId = UUID.randomUUID();
        CompletedLearningUnitInfo info = new CompletedLearningUnitInfo(
                learningUnitId,
                "Encapsulation",
                "Practice encapsulation",
                UUID.randomUUID(),
                "OOP",
                UUID.randomUUID(),
                "Foundations");
        when(aiClient.generateContent(eq(AiPurpose.QUIZ_GENERATION), anyString(), anyString()))
                .thenReturn("response");
        when(validator.validate("response", Set.of(learningUnitId)))
                .thenReturn(planWithQuestionCount(3), planWithQuestionCount(5));

        GeneratedQuizPlan result = generator.generateMasteryCheck(info);

        assertEquals(5, result.questions().size());
        verify(aiClient, times(2)).generateContent(
                eq(AiPurpose.QUIZ_GENERATION), anyString(), anyString());
    }

    private GeneratedQuizPlan planWithQuestionCount(int count) {
        return new GeneratedQuizPlan(
                Collections.nCopies(count, mock(GeneratedQuestion.class)));
    }
}

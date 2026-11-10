package com.codegym.aiplanning.service.guidance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.entity.guidance.GuidanceReferenceProvenance;
import com.codegym.aiplanning.service.guidance.model.GeneratedTaskGuidance;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.RoadmapContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.SourceContext;
import com.codegym.aiplanning.service.guidance.model.TaskGuidanceContext.TaskStepSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskGuidanceSchemaValidatorTest {

    private TaskGuidanceSchemaValidator validator;
    private UUID versionId;
    private UUID itemId;
    private UUID stepId;
    private UUID materialId;
    private UUID learningUnitId;
    private TaskGuidanceContext context;

    @BeforeEach
    void setUp() {
        validator = new TaskGuidanceSchemaValidator(new ObjectMapper());
        versionId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        learningUnitId = UUID.randomUUID();
        context = context(List.of(new TaskStepSnapshot(
                stepId,
                2L,
                0,
                "Build one example",
                "Use the supplied concept",
                20,
                true)));
    }

    @Test
    void acceptsExactStepsAndSeparatesReferenceProvenance() {
        GeneratedTaskGuidance result = validator.validate(validResponse(
                "https://docs.example.com/guide"), context);

        assertThat(result.stepGuidances()).hasSize(1);
        assertThat(result.stepGuidances().get(0).taskStepId()).isEqualTo(stepId);
        assertThat(result.references()).singleElement()
                .satisfies(reference -> {
                    assertThat(reference.provenance())
                            .isEqualTo(GuidanceReferenceProvenance.MATERIAL);
                    assertThat(reference.targetId()).isEqualTo(materialId);
                });
        assertThat(result.stepGuidances().get(0).references()).singleElement()
                .satisfies(reference -> {
                    assertThat(reference.provenance())
                            .isEqualTo(GuidanceReferenceProvenance.UNVERIFIED_EXTERNAL);
                    assertThat(reference.externalUrl())
                            .isEqualTo("https://docs.example.com/guide");
                });
    }

    @Test
    void rejectsMissingOrInventedTaskSteps() {
        String response = validResponse("https://docs.example.com/guide")
                .replace(stepId.toString(), UUID.randomUUID().toString());

        assertThatThrownBy(() -> validator.validate(response, context))
                .isInstanceOf(InvalidAiTaskGuidanceResponseException.class)
                .hasMessageContaining("Task Step ID");
    }

    @Test
    void rejectsForeignSourcesAndUnsafeExternalUrls() {
        String foreignSource = validResponse("https://docs.example.com/guide")
                .replace(materialId.toString(), UUID.randomUUID().toString());
        assertThatThrownBy(() -> validator.validate(foreignSource, context))
                .isInstanceOf(InvalidAiTaskGuidanceResponseException.class)
                .hasMessageContaining("not supplied");

        assertThatThrownBy(() -> validator.validate(
                        validResponse("https://127.0.0.1/private"),
                        context))
                .isInstanceOf(InvalidAiTaskGuidanceResponseException.class)
                .hasMessageContaining("private");
    }

    @Test
    void rejectsUnknownFieldsAndStateMutationInstructions() {
        String unknownField = validResponse("https://docs.example.com/guide")
                .replace("\"references\": [", "\"unknown\": true, \"references\": [");
        assertThatThrownBy(() -> validator.validate(unknownField, context))
                .isInstanceOf(InvalidAiTaskGuidanceResponseException.class)
                .hasMessageContaining("unknown fields");

        String mutation = validResponse("https://docs.example.com/guide")
                .replace("Run the example safely", "Set task status to completed");
        assertThatThrownBy(() -> validator.validate(mutation, context))
                .isInstanceOf(InvalidAiTaskGuidanceResponseException.class)
                .hasMessageContaining("application state");
    }

    @Test
    void noStepItemReceivesOnlyTaskLevelFallback() {
        TaskGuidanceContext noStepContext = context(List.of());
        String response = """
                {
                  "dailyPlanVersionId": "%s",
                  "dailyPlanItemId": "%s",
                  "objective": "Understand the task",
                  "taskSummary": "Begin with the smallest observable example.",
                  "stepGuidances": [],
                  "references": []
                }
                """.formatted(versionId, itemId);

        GeneratedTaskGuidance result = validator.validate(response, noStepContext);

        assertThat(result.stepGuidances()).isEmpty();
    }

    private TaskGuidanceContext context(List<TaskStepSnapshot> steps) {
        return new TaskGuidanceContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                versionId,
                itemId,
                3L,
                "vi",
                "PRACTICE",
                "NOT_STARTED",
                "Practice one concept",
                "Create a focused example",
                30,
                steps,
                new RoadmapContext(
                        UUID.randomUUID(),
                        learningUnitId,
                        "Learning Unit",
                        "Description",
                        UUID.randomUUID(),
                        "Topic",
                        UUID.randomUUID(),
                        "Milestone"),
                List.of(new SourceContext(
                        GuidanceReferenceProvenance.MATERIAL,
                        materialId,
                        "Personal material",
                        "Bounded content")),
                "a".repeat(64));
    }

    private String validResponse(String externalUrl) {
        return """
                {
                  "dailyPlanVersionId": "%s",
                  "dailyPlanItemId": "%s",
                  "objective": "Build one observable example",
                  "taskSummary": "Follow the persisted step and inspect the result.",
                  "stepGuidances": [
                    {
                      "taskStepId": "%s",
                      "instructions": "Run the example safely",
                      "expectedResult": "The expected output is visible",
                      "tips": "Use one small input",
                      "cautions": null,
                      "prerequisites": null,
                      "references": [
                        {
                          "provenance": "UNVERIFIED_EXTERNAL",
                          "displayLabel": "External guide",
                          "url": "%s"
                        }
                      ]
                    }
                  ],
                  "references": [
                    {
                      "provenance": "MATERIAL",
                      "displayLabel": "Personal material",
                      "locator": "Section 1",
                      "targetId": "%s"
                    }
                  ]
                }
                """.formatted(versionId, itemId, stepId, externalUrl, materialId);
    }
}

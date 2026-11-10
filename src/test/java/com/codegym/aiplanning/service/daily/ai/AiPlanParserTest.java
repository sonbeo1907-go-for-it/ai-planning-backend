package com.codegym.aiplanning.service.daily.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiPlanParserTest {

    private final AiPlanParser parser =
            new AiPlanParser(new ObjectMapper().findAndRegisterModules());

    @Test
    void rejectsStepWithoutActionAndScopeEvidence() {
        String response = """
                {
                  "summary": "Plan",
                  "items": [
                    {
                      "roadmapItemId": null,
                      "title": "Practice one concept",
                      "description": null,
                      "category": "PRACTICE",
                      "plannedMinutes": 30,
                      "aiAdjustmentAction": null,
                      "aiAdjustmentReason": null,
                      "steps": [
                        {
                          "title": "Write one example",
                          "guidance": null,
                          "orderIndex": 0,
                          "estimatedMinutes": 10,
                          "required": true
                        }
                      ]
                    }
                  ],
                  "adjustments": []
                }
                """;

        assertThatThrownBy(() -> parser.parse(response))
                .isInstanceOf(InvalidAiDailyPlanResponseException.class)
                .hasMessageContaining("fields do not match");
    }
}

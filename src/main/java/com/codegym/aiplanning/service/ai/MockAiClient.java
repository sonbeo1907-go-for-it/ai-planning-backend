package com.codegym.aiplanning.service.ai;

import org.springframework.stereotype.Service;

@Service
public class MockAiClient implements AiClient {
    @Override
    public String generate(String systemPrompt, String userPrompt) {
        // Mocked AI JSON Response based on US-PLN-AI requirements
        return """
        {
          "items": [
            {
              "roadmapItemId": null,
              "title": "Study Java Basics",
              "description": "Learn about primitive types",
              "category": "NEW_MATERIAL",
              "plannedMinutes": 60,
              "aiAdjustmentAction": "CARRY_OVER",
              "aiAdjustmentReason": "Carried over from yesterday"
            },
            {
              "roadmapItemId": null,
              "title": "Practice OOP",
              "description": "Do 3 exercises on inheritance",
              "category": "PRACTICE",
              "plannedMinutes": 90,
              "aiAdjustmentAction": null,
              "aiAdjustmentReason": null
            }
          ]
        }
        """;
    }
}

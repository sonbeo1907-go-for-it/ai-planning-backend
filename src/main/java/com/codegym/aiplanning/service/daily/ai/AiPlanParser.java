package com.codegym.aiplanning.service.daily.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiPlanParser {
    private final ObjectMapper objectMapper;

    public AiPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DailyPlanAiResponse parse(String rawJson) throws JsonProcessingException {
        return objectMapper.readValue(rawJson, DailyPlanAiResponse.class);
    }
}

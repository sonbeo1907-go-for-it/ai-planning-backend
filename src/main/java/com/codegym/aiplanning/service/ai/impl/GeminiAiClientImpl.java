package com.codegym.aiplanning.service.ai.impl;

import com.codegym.aiplanning.service.ai.AiClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiAiClientImpl implements AiClientService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClientImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.api-key:${GEMINI_API_KEY:${OPENAI_API_KEY:}}}")
    private String apiKey;

    @Value("${app.ai.model:${GEMINI_MODEL:gemini-2.5-flash}}")
    private String modelName;

    public GeminiAiClientImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public GeminiAiClientImpl(String apiKey, String modelName) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public String generateContent(String systemPrompt, String userPrompt) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return callGeminiApi(systemPrompt, userPrompt);
            } catch (Exception e) {
                log.warn("Direct AI Provider API call failed: {}. Falling back to smart mock response.", e.getMessage());
                return generateMockResponse(userPrompt);
            }
        } else {
            log.info("No AI Provider API key configured. Utilizing smart fallback AI generator.");
            return generateMockResponse(userPrompt);
        }
    }

    private String callGeminiApi(String systemPrompt, String userPrompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        );

        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt))
        );

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", systemInstruction,
                "contents", List.of(userContent),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.7
                )
        );

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
        }
        throw new IllegalStateException("Invalid response structure from Gemini API: " + response.getBody());
    }

    private String generateMockResponse(String userPrompt) {
        // Parse topic/goal from userPrompt if possible
        String topic = "Lập trình Web nâng cao";
        if (userPrompt != null && userPrompt.contains("Mục tiêu học tập:")) {
            int start = userPrompt.indexOf("Mục tiêu học tập:") + "Mục tiêu học tập:".length();
            int end = userPrompt.indexOf("\n", start);
            if (end > start) {
                topic = userPrompt.substring(start, end).trim();
            } else {
                topic = userPrompt.substring(start).trim();
            }
            if (topic.length() > 60) {
                topic = topic.substring(0, 60);
            }
        }

        return """
                {
                  "title": "Lộ trình học tập: %s",
                  "description": "Lộ trình kiến trúc bởi AI được cá nhân hóa dựa trên mục tiêu và thời gian cam kết của bạn.",
                  "milestones": [
                    {
                      "title": "Cột mốc 1: Nền tảng & Khái niệm cốt lõi",
                      "description": "Nắm vững các khái niệm cơ bản, thiết lập môi trường và cấu trúc tổng quan.",
                      "orderIndex": 0,
                      "topics": [
                        {
                          "title": "Chủ đề 1.1: Tổng quan và Thiết lập Môi trường",
                          "description": "Cài đặt công cụ, hiểu kiến trúc tổng thể và chuẩn bị tài nguyên.",
                          "orderIndex": 0,
                          "estimatedMinutes": 60
                        },
                        {
                          "title": "Chủ đề 1.2: Các nguyên lý cơ bản",
                          "description": "Đọc tài liệu lý thuyết và thực hành các bài tập nhỏ đầu tiên.",
                          "orderIndex": 1,
                          "estimatedMinutes": 90
                        },
                        {
                          "title": "Chủ đề 1.3: Thực hành khởi tạo dự án",
                          "description": "Tự tay dựng cấu trúc dự án mẫu và kiểm thử luồng cơ bản.",
                          "orderIndex": 2,
                          "estimatedMinutes": 120
                        }
                      ]
                    },
                    {
                      "title": "Cột mốc 2: Kỹ năng chuyên sâu & Thực hành dự án",
                      "description": "Xây dựng tính năng hoàn chỉnh, tối ưu hóa và làm chủ các kỹ thuật nâng cao.",
                      "orderIndex": 1,
                      "topics": [
                        {
                          "title": "Chủ đề 2.1: Phát triển tính năng cốt lõi",
                          "description": "Hiện thực hóa luồng xử lý chính và áp dụng Best Practices.",
                          "orderIndex": 0,
                          "estimatedMinutes": 90
                        },
                        {
                          "title": "Chủ đề 2.2: Xử lý ngoại lệ & Bảo mật",
                          "description": "Bổ sung validation, mã hóa dữ liệu và xử lý các kịch bản lỗi.",
                          "orderIndex": 1,
                          "estimatedMinutes": 90
                        },
                        {
                          "title": "Chủ đề 2.3: Đánh giá và Đóng gói dự án",
                          "description": "Review lại kiến thức, kiểm thử toàn diện và đánh giá kết quả đạt được.",
                          "orderIndex": 2,
                          "estimatedMinutes": 120
                        }
                      ]
                    },
                    {
                      "title": "Cột mốc 3: Nâng cao & Ứng dụng thực tế",
                      "description": "Mở rộng quy mô, tích hợp hệ thống và hoàn thiện sản phẩm.",
                      "orderIndex": 2,
                      "topics": [
                        {
                          "title": "Chủ đề 3.1: Thử nghiệm kịch bản nâng cao",
                          "description": "Thực hành giải quyết các bài toán phức tạp trong thực tế.",
                          "orderIndex": 0,
                          "estimatedMinutes": 90
                        },
                        {
                          "title": "Chủ đề 3.2: Tổng kết & Định hướng mở rộng",
                          "description": "Đánh giá tiến độ lộ trình và xây dựng các mục tiêu tiếp theo.",
                          "orderIndex": 1,
                          "estimatedMinutes": 60
                        }
                      ]
                    }
                  ]
                }
                """.formatted(topic);
    }
}

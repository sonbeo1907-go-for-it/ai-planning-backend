package com.codegym.aiplanning.service.roadmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codegym.aiplanning.service.roadmap.model.GeneratedRoadmapPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiRoadmapSchemaValidatorTest {

    private ObjectMapper objectMapper;
    private AiRoadmapSchemaValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new AiRoadmapSchemaValidator(objectMapper);
    }

    @Test
    void acceptsTheExactRoadmapSchema() throws Exception {
        GeneratedRoadmapPlan result = validator.validate(
                objectMapper.writeValueAsString(validRoadmap()));

        assertEquals(3, result.milestones().size());
        assertEquals(2, result.milestones().get(0).topics().size());
        assertEquals(60, result.milestones().get(0).topics().get(0).estimatedMinutes());
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        ObjectNode roadmap = validRoadmap();
        roadmap.put("providerResponse", "must not be accepted");

        assertThrows(
                InvalidAiRoadmapResponseException.class,
                () -> validator.validate(objectMapper.writeValueAsString(roadmap)));
    }

    @Test
    void rejectsInvalidEstimatedMinutesInsteadOfDefaultingIt() throws Exception {
        ObjectNode roadmap = validRoadmap();
        ObjectNode firstMilestone =
                (ObjectNode) roadmap.withArray("milestones").get(0);
        ObjectNode firstTopic =
                (ObjectNode) firstMilestone.withArray("topics").get(0);
        firstTopic.put("estimatedMinutes", 0);

        assertThrows(
                InvalidAiRoadmapResponseException.class,
                () -> validator.validate(objectMapper.writeValueAsString(roadmap)));
    }

    @Test
    void rejectsMilestoneCountOutsideTheSchema() throws Exception {
        ObjectNode roadmap = validRoadmap();
        roadmap.withArray("milestones").remove(2);

        assertThrows(
                InvalidAiRoadmapResponseException.class,
                () -> validator.validate(objectMapper.writeValueAsString(roadmap)));
    }

    @Test
    void rejectsMarkdownWrappedJson() throws Exception {
        String wrapped = "```json\n"
                + objectMapper.writeValueAsString(validRoadmap())
                + "\n```";

        assertThrows(
                InvalidAiRoadmapResponseException.class,
                () -> validator.validate(wrapped));
    }

    private ObjectNode validRoadmap() {
        ObjectNode roadmap = objectMapper.createObjectNode();
        roadmap.put("title", "Lộ trình Backend Java");
        roadmap.put("description", "Lộ trình học có cấu trúc");
        ArrayNode milestones = roadmap.putArray("milestones");
        for (int milestoneIndex = 0; milestoneIndex < 3; milestoneIndex++) {
            ObjectNode milestone = milestones.addObject();
            milestone.put("title", "Cột mốc " + milestoneIndex);
            milestone.put("description", "Mô tả cột mốc");
            milestone.put("orderIndex", milestoneIndex);
            ArrayNode topics = milestone.putArray("topics");
            for (int topicIndex = 0; topicIndex < 2; topicIndex++) {
                ObjectNode topic = topics.addObject();
                topic.put("title", "Chủ đề " + topicIndex);
                topic.put("description", "Mô tả chủ đề");
                topic.put("orderIndex", topicIndex);
                topic.put("estimatedMinutes", 60);
            }
        }
        return roadmap;
    }
}

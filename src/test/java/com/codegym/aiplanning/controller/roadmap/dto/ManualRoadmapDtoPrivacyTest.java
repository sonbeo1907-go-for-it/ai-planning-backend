package com.codegym.aiplanning.controller.roadmap.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.entity.roadmap.RoadmapItemType;
import com.codegym.aiplanning.entity.roadmap.RoadmapStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualRoadmapDtoPrivacyTest {

    @Test
    void requestAndResponseStringsRedactPersonalLearningContent() {
        String privateTitle = "Private certification preparation";
        String privateDescription = "Personal study notes and weak topics";

        CreateRoadmapRequest request =
                new CreateRoadmapRequest(privateTitle, privateDescription);
        RoadmapItemResponse item = new RoadmapItemResponse(
                UUID.randomUUID(),
                0,
                RoadmapItemType.TOPIC,
                UUID.randomUUID(),
                privateTitle,
                privateDescription,
                0,
                60,
                List.of());
        RoadmapResponse response = new RoadmapResponse(
                UUID.randomUUID(),
                0,
                privateTitle,
                privateDescription,
                RoadmapStatus.DRAFT,
                null,
                List.of(),
                null,
                null);

        assertThat(request.toString())
                .contains("<redacted>")
                .doesNotContain(privateTitle, privateDescription);
        assertThat(item.toString())
                .contains("<redacted>")
                .doesNotContain(privateTitle, privateDescription);
        assertThat(response.toString())
                .contains("<redacted>")
                .doesNotContain(privateTitle, privateDescription);
    }
}

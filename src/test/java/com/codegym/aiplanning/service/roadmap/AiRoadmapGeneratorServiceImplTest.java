package com.codegym.aiplanning.service.roadmap;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.roadmap.dto.RoadmapVersionResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.roadmap.Roadmap;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersion;
import com.codegym.aiplanning.entity.roadmap.RoadmapVersionOrigin;
import com.codegym.aiplanning.repository.roadmap.RoadmapItemRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapSourceRepository;
import com.codegym.aiplanning.repository.roadmap.RoadmapVersionRepository;
import com.codegym.aiplanning.service.ai.AiClientService;
import com.codegym.aiplanning.service.audit.AuditLogService;
import com.codegym.aiplanning.service.roadmap.impl.AiRoadmapGeneratorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRoadmapGeneratorServiceImplTest {

    @Mock
    private RoadmapRepository roadmapRepository;

    @Mock
    private RoadmapVersionRepository roadmapVersionRepository;

    @Mock
    private RoadmapItemRepository roadmapItemRepository;

    @Mock
    private RoadmapSourceRepository roadmapSourceRepository;

    @Mock
    private AiClientService aiClientService;

    @Mock
    private AuditLogService auditLogService;

    private AiRoadmapGeneratorServiceImpl service;

    private UUID userId;
    private UUID roadmapId;
    private UserAccount owner;
    private Roadmap roadmap;

    @BeforeEach
    void setUp() {
        service = new AiRoadmapGeneratorServiceImpl(
                roadmapRepository,
                roadmapVersionRepository,
                roadmapItemRepository,
                roadmapSourceRepository,
                aiClientService,
                auditLogService
        );

        userId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();

        owner = UserAccount.create("test@example.com", "passwordHash", UserRole.USER, com.codegym.aiplanning.entity.auth.AccountStatus.ACTIVE);
        roadmap = Roadmap.beginOnboarding(owner);
        org.springframework.test.util.ReflectionTestUtils.setField(roadmap, "id", roadmapId);
    }

    @Test
    void generate_SuccessOnFirstAttempt() {
        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId)).thenReturn(Optional.of(roadmap));
        when(roadmapSourceRepository.findByRoadmapId(roadmapId)).thenReturn(List.of());

        String validJson = """
                {
                  "title": "Lộ trình Java Spring Boot",
                  "description": "Lộ trình AI sinh tự động",
                  "milestones": [
                    {
                      "title": "Cột mốc 1: Căn bản Java",
                      "description": "Học cú pháp Java",
                      "orderIndex": 0,
                      "topics": [
                        {
                          "title": "Topic 1.1: Biến và Kiểu dữ liệu",
                          "description": "Biến cơ bản",
                          "orderIndex": 0,
                          "estimatedMinutes": 60
                        }
                      ]
                    }
                  ]
                }
                """;

        when(aiClientService.generateContent(anyString(), anyString())).thenReturn(validJson);

        RoadmapVersion mockVersion = RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.AI_GENERATED);
        org.springframework.test.util.ReflectionTestUtils.setField(mockVersion, "id", UUID.randomUUID());
        when(roadmapVersionRepository.findFirstByRoadmapIdOrderByVersionNumberDesc(roadmap.getId())).thenReturn(Optional.empty());
        when(roadmapVersionRepository.saveAndFlush(any(RoadmapVersion.class))).thenReturn(mockVersion);
        when(roadmapVersionRepository.findById(any())).thenReturn(Optional.of(mockVersion));
        when(roadmapItemRepository.findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(any(), any())).thenReturn(List.of());

        RoadmapVersionResponse response = service.generate(userId, roadmapId);

        assertNotNull(response);
        assertEquals(1, mockVersion.getVersionNumber());
        assertEquals(RoadmapVersionOrigin.AI_GENERATED, mockVersion.getOrigin());
        verify(aiClientService, times(1)).generateContent(anyString(), anyString());
    }

    @Test
    void generate_RetryOnInvalidJson_SuccessOnSecondAttempt() {
        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId)).thenReturn(Optional.of(roadmap));
        when(roadmapSourceRepository.findByRoadmapId(roadmapId)).thenReturn(List.of());

        String invalidJson = "Invalid JSON response from AI";
        String validJson = """
                {
                  "title": "Lộ trình Java Spring Boot",
                  "milestones": [
                    {
                      "title": "Cột mốc 1",
                      "topics": [{"title": "Topic 1", "estimatedMinutes": 60}]
                    }
                  ]
                }
                """;

        when(aiClientService.generateContent(anyString(), anyString()))
                .thenReturn(invalidJson)
                .thenReturn(validJson);

        RoadmapVersion mockVersion = RoadmapVersion.draft(roadmap, 1, RoadmapVersionOrigin.AI_GENERATED);
        org.springframework.test.util.ReflectionTestUtils.setField(mockVersion, "id", UUID.randomUUID());
        when(roadmapVersionRepository.findFirstByRoadmapIdOrderByVersionNumberDesc(roadmap.getId())).thenReturn(Optional.empty());
        when(roadmapVersionRepository.saveAndFlush(any(RoadmapVersion.class))).thenReturn(mockVersion);
        when(roadmapVersionRepository.findById(any())).thenReturn(Optional.of(mockVersion));
        when(roadmapItemRepository.findAllByRoadmapVersionIdAndItemTypeAndParentIsNullOrderByOrderIndexAsc(any(), any())).thenReturn(List.of());

        RoadmapVersionResponse response = service.generate(userId, roadmapId);

        assertNotNull(response);
        verify(aiClientService, times(2)).generateContent(anyString(), anyString());
    }

    @Test
    void generate_ThrowsExceptionWhenAllAttemptsFail() {
        when(roadmapRepository.findOwnedByIdForUpdate(roadmapId, userId)).thenReturn(Optional.of(roadmap));
        when(roadmapSourceRepository.findByRoadmapId(roadmapId)).thenReturn(List.of());

        when(aiClientService.generateContent(anyString(), anyString())).thenReturn("Corrupted response");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.generate(userId, roadmapId));
        assertEquals(ErrorCode.AI_GENERATION_FAILED, exception.errorCode());
        verify(aiClientService, times(3)).generateContent(anyString(), anyString());
    }
}

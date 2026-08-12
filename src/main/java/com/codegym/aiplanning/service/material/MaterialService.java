package com.codegym.aiplanning.service.material;

import com.codegym.aiplanning.controller.material.dto.CreateTextMaterialRequest;
import com.codegym.aiplanning.controller.material.dto.MaterialResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MaterialService {
    MaterialResponse uploadMaterial(UUID userId, MultipartFile file);
    MaterialResponse createFromText(UUID userId, CreateTextMaterialRequest request);
}

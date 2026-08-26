package com.codegym.aiplanning.service.material;

import com.codegym.aiplanning.controller.material.dto.CreateTextMaterialRequest;
import com.codegym.aiplanning.controller.material.dto.MaterialResponse;
import org.springframework.web.multipart.MultipartFile;

import com.codegym.aiplanning.controller.material.dto.MaterialListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;

public interface MaterialService {
    MaterialResponse uploadMaterial(UUID userId, MultipartFile file);
    MaterialResponse createFromText(UUID userId, CreateTextMaterialRequest request);
    Page<MaterialListResponse> getMyMaterials(
            UUID userId,
            String query,
            MaterialType type,
            MaterialStatus status,
            Pageable pageable);
    void archiveMaterial(UUID userId, UUID materialId);
}

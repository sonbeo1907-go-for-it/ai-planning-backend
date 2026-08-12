package com.codegym.aiplanning.service.material;

import com.codegym.aiplanning.controller.material.dto.MaterialUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MaterialService {
    MaterialUploadResponse uploadMaterial(UUID userId, MultipartFile file);
}

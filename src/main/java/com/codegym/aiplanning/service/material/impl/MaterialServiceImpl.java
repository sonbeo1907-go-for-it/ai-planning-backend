package com.codegym.aiplanning.service.material.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.controller.material.dto.CreateTextMaterialRequest;
import com.codegym.aiplanning.controller.material.dto.MaterialResponse;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.entity.material.MaterialType;
import com.codegym.aiplanning.repository.MaterialRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.service.material.MaterialExtractionService;
import com.codegym.aiplanning.service.material.MaterialService;
import com.codegym.aiplanning.service.material.StorageService;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.codegym.aiplanning.controller.material.dto.MaterialListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class MaterialServiceImpl implements MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialServiceImpl.class);

    private final MaterialRepository materialRepository;
    private final UserAccountRepository userAccountRepository;
    private final StorageService storageService;
    private final MaterialExtractionService materialExtractionService;
    private final Tika tika;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
            "application/x-tika-msoffice", // encrypted docx / older doc
            "application/x-tika-ooxml-protected", // encrypted docx
            "text/plain"
    );

    public MaterialServiceImpl(MaterialRepository materialRepository,
                               UserAccountRepository userAccountRepository,
                               StorageService storageService,
                               MaterialExtractionService materialExtractionService) {
        this.materialRepository = materialRepository;
        this.userAccountRepository = userAccountRepository;
        this.storageService = storageService;
        this.materialExtractionService = materialExtractionService;
        this.tika = new Tika();
    }

    @Override
    @Transactional
    public MaterialResponse uploadMaterial(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "File is empty or missing.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Original file name is missing.");
        }

        String extension = getExtension(originalFilename);
        if (!Set.of("pdf", "docx", "txt").contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Invalid file extension. Only pdf, docx, txt are allowed.");
        }

        String detectedMimeType;
        try {
            detectedMimeType = tika.detect(file.getInputStream(), originalFilename);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Could not detect file content type.");
        }

        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Detected content type is not allowed: " + detectedMimeType);
        }

        if (extension.equals("pdf") && !detectedMimeType.equals("application/pdf")) {
            log.warn("SECURITY WARNING: File extension spoofing detected. Expected pdf, got {}", detectedMimeType);
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "File content does not match pdf extension.");
        }
        if (extension.equals("docx") && !(detectedMimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                       || detectedMimeType.equals("application/x-tika-msoffice")
                                       || detectedMimeType.equals("application/x-tika-ooxml-protected"))) {
            log.warn("SECURITY WARNING: File extension spoofing detected. Expected docx, got {}", detectedMimeType);
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "File content does not match docx extension.");
        }
        if (extension.equals("txt") && !detectedMimeType.equals("text/plain")) {
            log.warn("SECURITY WARNING: File extension spoofing detected. Expected txt, got {}", detectedMimeType);
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "File content does not match txt extension.");
        }

        String storageKey = userId + "/" + UUID.randomUUID() + "." + extension;

        storageService.store(file, storageKey);

        try {
            UserAccount user = userAccountRepository.getReferenceById(userId);
            Material material = Material.create(user, originalFilename, detectedMimeType, file.getSize(), storageKey);
            Material saved = materialRepository.save(material);

            // Trigger background extraction after transaction commits
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            materialExtractionService.extractTextAsync(saved.getId());
                        }
                    }
                );
            } else {
                materialExtractionService.extractTextAsync(saved.getId());
            }

            return new MaterialResponse(
                    saved.getId(),
                    saved.getOriginalFileName(),
                    saved.getContentType(),
                    saved.getFileSize(),
                    saved.getCreatedAt(),
                    saved.getType(),
                    saved.getStatus(),
                    null, // Không trả content cho FILE
                    saved.getErrorCode(),
                    saved.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("Failed to save material metadata to DB. Cleaning up storage file: {}", storageKey, e);
            storageService.delete(storageKey);
            throw e;
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    @Override
    @Transactional
    public MaterialResponse createFromText(UUID userId, CreateTextMaterialRequest request) {
        if (request.getType() == MaterialType.FILE) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Type FILE is not allowed for text material.");
        }

        UserAccount user = userAccountRepository.getReferenceById(userId);

        Material material = Material.createText(user, request.getType(), request.getContent());
        Material saved = materialRepository.save(material);

        log.info("User {} created {} material with ID {}", userId, saved.getType(), saved.getId());

        return new MaterialResponse(
                saved.getId(),
                saved.getOriginalFileName(),
                saved.getContentType(),
                saved.getFileSize(),
                saved.getCreatedAt(),
                saved.getType(),
                saved.getStatus(),
                saved.getContent(),
                saved.getErrorCode(),
                saved.getErrorMessage()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaterialListResponse> getMyMaterials(
            UUID userId,
            String query,
            MaterialType type,
            MaterialStatus status,
            Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        Page<Material> materials = materialRepository.searchOwnedActive(
                userId, normalizedQuery, type, status, pageable);
        return materials.map(MaterialListResponse::from);
    }

    @Override
    @Transactional
    public void archiveMaterial(UUID userId, UUID materialId) {
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Material not found or not owned by user."));

        if (material.isArchived()) {
            log.info("Material {} is already archived. Ignoring.", materialId);
            return;
        }

        material.archive();
        materialRepository.save(material);
        log.info("User {} archived material {}", userId, materialId);
    }
}

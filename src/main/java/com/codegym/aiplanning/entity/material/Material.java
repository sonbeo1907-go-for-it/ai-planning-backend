package com.codegym.aiplanning.entity.material;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "materials")
public class Material extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MaterialType type = MaterialType.FILE;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MaterialStatus status = MaterialStatus.READY;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "storage_key", unique = true)
    private String storageKey;

    @Column(name = "error_code", length = 50)
    @Enumerated(EnumType.STRING)
    private ExtractionErrorCode errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_started_at")
    private java.time.Instant processingStartedAt;

    protected Material() {}

    public static Material create(UserAccount user, String originalFileName, String contentType, Long fileSize, String storageKey) {
        Material material = new Material();
        material.user = user;
        material.type = MaterialType.FILE;
        material.status = MaterialStatus.PENDING; // Luôn bắt đầu bằng PENDING để chờ extract
        material.originalFileName = originalFileName;
        material.contentType = contentType;
        material.fileSize = fileSize;
        material.storageKey = storageKey;
        return material;
    }

    public static Material createText(UserAccount user, MaterialType type, String content) {
        Material material = new Material();
        material.user = user;
        material.type = type;
        material.status = MaterialStatus.READY; // Text/Goal thì có sẵn content nên READY luôn
        material.content = content;
        return material;
    }

    public void markAsProcessing() {
        if (this.status != MaterialStatus.PENDING) {
            throw new IllegalStateException("Only PENDING materials can be marked as PROCESSING.");
        }
        this.status = MaterialStatus.PROCESSING;
        this.processingStartedAt = java.time.Instant.now();
    }

    public void markAsReady(String extractedText) {
        if (this.status != MaterialStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING materials can be marked as READY.");
        }
        this.status = MaterialStatus.READY;
        this.content = extractedText;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markAsFailed(ExtractionErrorCode code, String message) {
        if (this.status != MaterialStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING materials can be marked as FAILED.");
        }
        if (code == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("ErrorCode and ErrorMessage must be provided when marking as FAILED.");
        }
        this.status = MaterialStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public MaterialType getType() {
        return type;
    }

    public MaterialStatus getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public ExtractionErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public java.time.Instant getProcessingStartedAt() {
        return processingStartedAt;
    }
}

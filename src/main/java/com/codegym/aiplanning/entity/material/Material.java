package com.codegym.aiplanning.entity.material;

import com.codegym.aiplanning.common.entity.BaseEntity;
import com.codegym.aiplanning.entity.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    protected Material() {}

    public static Material create(UserAccount user, String originalFileName, String contentType, Long fileSize, String storageKey) {
        Material material = new Material();
        material.user = user;
        material.originalFileName = originalFileName;
        material.contentType = contentType;
        material.fileSize = fileSize;
        material.storageKey = storageKey;
        return material;
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
}

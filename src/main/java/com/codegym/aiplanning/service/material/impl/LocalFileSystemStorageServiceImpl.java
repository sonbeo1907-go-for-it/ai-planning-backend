package com.codegym.aiplanning.service.material.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.service.material.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileSystemStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorageServiceImpl.class);
    private final Path rootLocation;

    public LocalFileSystemStorageServiceImpl(@Value("${app.storage.local.upload-dir:./uploads/materials}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    @Override
    public void store(MultipartFile file, String storageKey) {
        try {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Failed to store empty file.");
            }
            Path destinationFile = this.rootLocation.resolve(Paths.get(storageKey))
                    .normalize().toAbsolutePath();
            
            if (!destinationFile.getParent().startsWith(this.rootLocation)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Cannot store file outside current directory.");
            }
            
            Files.createDirectories(destinationFile.getParent());
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store file with storage key: {}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Failed to store file.");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path file = rootLocation.resolve(Paths.get(storageKey)).normalize().toAbsolutePath();
            if (file.getParent().startsWith(this.rootLocation)) {
                Files.deleteIfExists(file);
                log.info("Deleted file during cleanup: {}", storageKey);
            }
        } catch (IOException e) {
            log.error("Failed to delete file during cleanup: {}", storageKey, e);
        }
    }

    @Override
    public java.io.InputStream load(String storageKey) {
        try {
            Path file = rootLocation.resolve(Paths.get(storageKey)).normalize().toAbsolutePath();
            if (file.getParent().startsWith(this.rootLocation) && Files.exists(file)) {
                return Files.newInputStream(file);
            }
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "File not found on local storage.");
        } catch (IOException e) {
            log.error("Failed to load file with storage key: {}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Failed to load file.");
        }
    }
}

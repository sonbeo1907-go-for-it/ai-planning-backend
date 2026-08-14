package com.codegym.aiplanning.service.material.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileSystemStorageServiceImplTest {

    Path storageRoot;

    @BeforeEach
    void createStorageRoot() throws Exception {
        storageRoot = Path.of("target", "test-storage", UUID.randomUUID().toString())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(storageRoot);
    }

    @AfterEach
    void removeStorageRoot() throws Exception {
        if (!Files.exists(storageRoot)) {
            return;
        }
        try (var paths = Files.walk(storageRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void storeLoadAndDeleteRemainInsideConfiguredRoot() throws Exception {
        LocalFileSystemStorageServiceImpl storage =
                new LocalFileSystemStorageServiceImpl(storageRoot.toString());
        storage.init();
        String storageKey = UUID.randomUUID() + "/" + UUID.randomUUID() + ".txt";
        byte[] content = "personal learning material".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file =
                new MockMultipartFile("file", "source.txt", "text/plain", content);

        storage.store(file, storageKey);

        Path storedFile = storageRoot.resolve(storageKey).normalize();
        assertThat(storedFile).exists();
        try (InputStream inputStream = storage.load(storageKey)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }

        storage.delete(storageKey);

        assertThat(storedFile).doesNotExist();
    }

    @Test
    void storeRejectsPathTraversal() {
        LocalFileSystemStorageServiceImpl storage =
                new LocalFileSystemStorageServiceImpl(storageRoot.toString());
        storage.init();
        String escapedFileName = "escape-" + UUID.randomUUID() + ".txt";
        MockMultipartFile file =
                new MockMultipartFile("file", "source.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> storage.store(file, "../" + escapedFileName))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.FILE_STORAGE_ERROR);

        assertThat(storageRoot.getParent().resolve(escapedFileName)).doesNotExist();
    }

    @Test
    void loadRejectsMissingStorageKey() {
        LocalFileSystemStorageServiceImpl storage =
                new LocalFileSystemStorageServiceImpl(storageRoot.toString());
        storage.init();

        assertThatThrownBy(() -> storage.load(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.FILE_STORAGE_ERROR);
    }
}

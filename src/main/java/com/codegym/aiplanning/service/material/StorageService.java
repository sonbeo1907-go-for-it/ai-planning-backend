package com.codegym.aiplanning.service.material;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    void store(MultipartFile file, String storageKey);
    void delete(String storageKey);
}

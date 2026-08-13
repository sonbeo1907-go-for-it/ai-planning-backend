package com.codegym.aiplanning.service.material.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.entity.material.ExtractionErrorCode;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.repository.MaterialRepository;
import com.codegym.aiplanning.service.material.MaterialExtractionService;
import com.codegym.aiplanning.service.material.StorageService;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.util.UUID;

@Service
public class MaterialExtractionServiceImpl implements MaterialExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MaterialExtractionServiceImpl.class);
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 50000;

    private final MaterialRepository materialRepository;
    private final StorageService storageService;
    private final TransactionTemplate transactionTemplate;

    public MaterialExtractionServiceImpl(MaterialRepository materialRepository, StorageService storageService, TransactionTemplate transactionTemplate) {
        this.materialRepository = materialRepository;
        this.storageService = storageService;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("materialExtractionExecutor")
    @Override
    public void extractTextAsync(UUID materialId) {
        log.info("Starting background extraction for material: {}", materialId);

        // 1. Atomic Claim using TransactionTemplate to ensure proxy boundary
        int updatedRows = transactionTemplate.execute(status -> {
            return materialRepository.claimForProcessing(materialId, java.time.Instant.now());
        });

        if (updatedRows != 1) {
            log.info("Failed to claim material {} for processing. It might be processed by another worker or not in PENDING state.", materialId);
            return;
        }

        // 2. Fetch the material object
        Material material = transactionTemplate.execute(status -> materialRepository.findById(materialId).orElse(null));
        if (material == null) {
            log.warn("Material {} claimed but not found in DB.", materialId);
            return;
        }

        ExtractionErrorCode errorCode = null;
        String errorMessage = null;
        String extractedText = null;

        // 3. Load & Parse
        try (InputStream inputStream = storageService.load(material.getStorageKey())) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, material.getContentType());
            metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, material.getOriginalFileName());
            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);
            extractedText = handler.toString();

        } catch (BusinessException e) {
            // Exceptions from StorageService
            log.error("Storage error for material {}: {}", materialId, e.getMessage());
            errorCode = ExtractionErrorCode.STORAGE_READ_FAILED;
            errorMessage = "Failed to load file from storage: " + e.getMessage();
        } catch (EncryptedDocumentException e) {
            log.warn("Encrypted document for material {}: {}", materialId, e.getMessage());
            errorCode = ExtractionErrorCode.PASSWORD_PROTECTED;
            errorMessage = "File bị bảo vệ bằng mật khẩu";
        } catch (SAXException e) {
            if (e.getCause() instanceof WriteLimitReachedException || e instanceof WriteLimitReachedException) {
                log.warn("Content limit exceeded for material {}: {}", materialId, e.getMessage());
                errorCode = ExtractionErrorCode.CONTENT_TOO_LARGE;
                errorMessage = "Lượng nội dung vượt quá giới hạn " + MAX_EXTRACTED_TEXT_LENGTH + " ký tự";
            } else {
                log.error("SAX error (malformed document) for material {}: {}", materialId, e.getMessage());
                errorCode = ExtractionErrorCode.CORRUPTED_FILE;
                errorMessage = "File lỗi cấu trúc, không đọc được nội dung chữ";
            }
        } catch (TikaException e) {
            log.error("Tika error (malformed document) for material {}: {}", materialId, e.getMessage());
            errorCode = ExtractionErrorCode.CORRUPTED_FILE;
            errorMessage = "File lỗi cấu trúc, không đọc được nội dung chữ";
        } catch (Exception e) {
            log.error("Unknown extraction error for material {}: {}", materialId, e.getMessage());
            errorCode = ExtractionErrorCode.EXTRACTION_FAILED;
            errorMessage = "Lỗi xử lý file không xác định: " + e.getMessage();
        }

        // 4. Content length / blank check
        if (errorCode == null) {
            if (extractedText == null || extractedText.isBlank()) {
                errorCode = ExtractionErrorCode.EMPTY_CONTENT;
                errorMessage = "File rỗng hoặc không chứa nội dung chữ hợp lệ";
            } else {
                // UNTRUSTED DATA - Do not log file content
                // extractedText is saved directly
            }
        }

        // 5. Update Status Transactionally
        final ExtractionErrorCode finalErrorCode = errorCode;
        final String finalErrorMessage = errorMessage;
        final String finalExtractedText = extractedText;

        transactionTemplate.executeWithoutResult(status -> {
            Material m = materialRepository.findById(materialId).orElseThrow();
            if (finalErrorCode != null) {
                m.markAsFailed(finalErrorCode, finalErrorMessage);
            } else {
                m.markAsReady(finalExtractedText);
            }
            materialRepository.save(m);
        });

        log.info("Finished background extraction for material: {}. ErrorCode: {}", materialId, errorCode);
    }
}

package com.codegym.aiplanning.service.material.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.material.ExtractionErrorCode;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.repository.MaterialRepository;
import com.codegym.aiplanning.service.material.StorageService;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialExtractionServiceImplTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private MaterialExtractionServiceImpl extractionService;

    private Material material;
    private UUID materialId;
    
    private MockedConstruction<AutoDetectParser> mockedParser;

    @BeforeEach
    void setUp() {
        extractionService = new MaterialExtractionServiceImpl(materialRepository, storageService, transactionTemplate);
        
        UserAccount user = UserAccount.create("user", "user@example.com", "hash", "User", UserRole.USER, AccountStatus.ACTIVE);
        material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        materialId = UUID.randomUUID();
        
        // Mock transaction template to just execute the callback directly
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        
        when(materialRepository.claimForProcessing(eq(materialId), any())).thenReturn(1);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        
        InputStream dummyStream = new ByteArrayInputStream("dummy".getBytes());
        when(storageService.load("s3://key")).thenReturn(dummyStream);
    }
    
    @AfterEach
    void tearDown() {
        if (mockedParser != null) {
            mockedParser.close();
        }
    }

    @Test
    void extractTextAsync_Success() throws Exception {
        // Arrange
        InputStream validStream = new ByteArrayInputStream("Extracted Text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(storageService.load("s3://key")).thenReturn(validStream);
        
        material.markAsProcessing(); 
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.READY);
        assertThat(material.getContent().trim()).isEqualTo("Extracted Text");
        assertThat(material.getErrorCode()).isNull();
    }

    @Test
    void extractTextAsync_EncryptedDocumentException() {
        // Arrange
        mockedParser = Mockito.mockConstruction(AutoDetectParser.class, (mock, context) -> {
            doThrow(new EncryptedDocumentException()).when(mock).parse(any(InputStream.class), any(ContentHandler.class), any(Metadata.class), any(ParseContext.class));
        });
        
        material.markAsProcessing();
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.PASSWORD_PROTECTED);
    }

    @Test
    void extractTextAsync_WriteLimitReached() {
        // Arrange
        mockedParser = Mockito.mockConstruction(AutoDetectParser.class, (mock, context) -> {
            doThrow(new SAXException(new WriteLimitReachedException(50000))).when(mock).parse(any(InputStream.class), any(ContentHandler.class), any(Metadata.class), any(ParseContext.class));
        });
        
        material.markAsProcessing();
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.CONTENT_TOO_LARGE);
    }

    @Test
    void extractTextAsync_CorruptedFile() {
        // Arrange
        mockedParser = Mockito.mockConstruction(AutoDetectParser.class, (mock, context) -> {
            doThrow(new TikaException("Malformed")).when(mock).parse(any(InputStream.class), any(ContentHandler.class), any(Metadata.class), any(ParseContext.class));
        });
        
        material.markAsProcessing();
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.CORRUPTED_FILE);
    }

    @Test
    void extractTextAsync_EmptyContent() throws Exception {
        // Arrange
        mockedParser = Mockito.mockConstruction(AutoDetectParser.class, (mock, context) -> {
            // Does nothing, meaning handler has no characters
        });
        
        material.markAsProcessing();
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.EMPTY_CONTENT);
    }
    
    @Test
    void extractTextAsync_StorageReadFailed() {
        // Arrange
        reset(storageService);
        when(storageService.load(anyString())).thenThrow(new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "S3 Down"));
        
        material.markAsProcessing();
        
        // Act
        extractionService.extractTextAsync(materialId);
        
        // Assert
        verify(materialRepository).save(material);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.STORAGE_READ_FAILED);
    }
}

package com.codegym.aiplanning.service.material.impl;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    private S3StorageServiceImpl s3StorageService;

    private final String bucketName = "test-bucket";

    @BeforeEach
    void setUp() throws Exception {
        s3StorageService = new S3StorageServiceImpl(
                "http://localhost:9000",
                bucketName,
                "accessKey",
                "secretKey",
                "us-east-1",
                1000,
                1000,
                3
        );

        // Inject the mocked S3Client via reflection since we instantiate it inside the constructor
        Field s3ClientField = S3StorageServiceImpl.class.getDeclaredField("s3Client");
        s3ClientField.setAccessible(true);
        s3ClientField.set(s3StorageService, s3Client);
    }

    @Test
    void testStoreSuccess() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        String storageKey = "materials/user-1/uuid.pdf";

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3StorageService.store(file, storageKey));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(bucketName, capturedRequest.bucket());
        assertEquals(storageKey, capturedRequest.key());
        assertEquals("application/pdf", capturedRequest.contentType());
        assertEquals(file.getSize(), capturedRequest.contentLength());
    }

    @Test
    void testStoreEmptyFileThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        String storageKey = "materials/user-1/uuid.pdf";

        BusinessException exception = assertThrows(BusinessException.class, () -> s3StorageService.store(file, storageKey));
        assertEquals(ErrorCode.INVALID_FILE_TYPE, exception.errorCode());
        
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testStoreS3Exception() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        String storageKey = "materials/user-1/uuid.pdf";

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        BusinessException exception = assertThrows(BusinessException.class, () -> s3StorageService.store(file, storageKey));
        assertEquals(ErrorCode.FILE_STORAGE_ERROR, exception.errorCode());
    }

    @Test
    void testDeleteSuccess() {
        String storageKey = "materials/user-1/uuid.pdf";

        assertDoesNotThrow(() -> s3StorageService.delete(storageKey));

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(requestCaptor.capture());

        DeleteObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(bucketName, capturedRequest.bucket());
        assertEquals(storageKey, capturedRequest.key());
    }

    @Test
    void testDeleteS3ExceptionDoesNotThrow() {
        String storageKey = "materials/user-1/uuid.pdf";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 delete error").build());

        // Should not throw because we catch it to log as ERROR for orphan cleanup
        assertDoesNotThrow(() -> s3StorageService.delete(storageKey));
    }
}

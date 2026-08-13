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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageServiceImpl(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.bucket-name}") String bucketName,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.api-call-timeout-ms:30000}") long apiCallTimeout,
            @Value("${app.storage.s3.api-call-attempt-timeout-ms:10000}") long apiCallAttemptTimeout,
            @Value("${app.storage.s3.max-retries:3}") int maxRetries) {

        this.bucketName = bucketName;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(apiCallTimeout))
                .apiCallAttemptTimeout(Duration.ofMillis(apiCallAttemptTimeout))
                .retryPolicy(RetryPolicy.builder().numRetries(maxRetries).build())
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .overrideConfiguration(overrideConfig)
                .build();
    }

    @Override
    public void store(MultipartFile file, String storageKey) {
        try {
            if (file.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Failed to store empty file.");
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Successfully uploaded file to S3: {}", storageKey);
        } catch (S3Exception e) {
            log.error("S3 error occurred while storing file: {}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Failed to store file on S3.");
        } catch (IOException e) {
            log.error("Failed to read file input stream for: {}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Failed to read file.");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Deleted file during cleanup from S3: {}", storageKey);
        } catch (S3Exception e) {
            // Log as ERROR to allow for future orphan cleanup processes
            log.error("CRITICAL: Failed to delete file during cleanup from S3. Orphan file detected. Bucket: {}, Key: {}", bucketName, storageKey, e);
        }
    }

    @Override
    public java.io.InputStream load(String storageKey) {
        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
            return s3Client.getObject(getObjectRequest);
        } catch (S3Exception e) {
            log.error("S3 error occurred while loading file: {}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Failed to read file from S3.");
        }
    }
}

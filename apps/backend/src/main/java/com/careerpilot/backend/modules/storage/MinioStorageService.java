package com.careerpilot.backend.modules.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

@Service
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "storage.type", havingValue = "s3", matchIfMissing = true)
public class MinioStorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${storage.s3.access-key}")
    private String accessKey;

    @Value("${storage.s3.secret-key}")
    private String secretKey;

    @Value("${storage.s3.bucket-name}")
    private String bucketName;

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    public MinioStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @PostConstruct
    public void init() {
        StorageStatus health = checkHealth();
        if (health == StorageStatus.HEALTHY) {
            log.info("S3 Bucket '{}' verified successfully.", bucketName);
        } else if (health == StorageStatus.BUCKET_NOT_FOUND) {
            try {
                log.info("S3 Bucket '{}' does not exist. Creating bucket.", bucketName);
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createBucketRequest);
                log.info("S3 Bucket '{}' created successfully.", bucketName);
            } catch (Exception e) {
                log.error("Failed to create storage bucket '{}': {}", bucketName, e.getMessage());
            }
        } else {
            log.error("Failed to initialize storage service bucket: status={}", health);
        }
    }

    @Override
    public StorageStatus checkHealth() {
        if (accessKey == null || accessKey.trim().isEmpty() || "dummy".equalsIgnoreCase(accessKey) ||
            secretKey == null || secretKey.trim().isEmpty() || "dummy".equalsIgnoreCase(secretKey)) {
            return StorageStatus.NOT_CONFIGURED;
        }
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            return StorageStatus.HEALTHY;
        } catch (NoSuchBucketException e) {
            log.warn("[STORAGE] MinIO health check failed\nstatus=BUCKET_NOT_FOUND\nendpoint={}\nreason={}", endpoint, e.getMessage());
            return StorageStatus.BUCKET_NOT_FOUND;
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                log.warn("[STORAGE] MinIO health check failed\nstatus=ACCESS_DENIED\nendpoint={}\nreason={}", endpoint, e.getMessage());
                return StorageStatus.ACCESS_DENIED;
            } else if (e.statusCode() == 401) {
                log.warn("[STORAGE] MinIO health check failed\nstatus=AUTHENTICATION_FAILED\nendpoint={}\nreason={}", endpoint, e.getMessage());
                return StorageStatus.AUTHENTICATION_FAILED;
            }
            log.warn("[STORAGE] MinIO health check failed\nstatus=FAILED\nendpoint={}\nreason={}", endpoint, e.getMessage());
            return StorageStatus.FAILED;
        } catch (Exception e) {
            String msg = e.getMessage();
            StorageStatus status = StorageStatus.UNAVAILABLE;
            if (msg != null && (msg.contains("Connection refused") || msg.contains("Connection timeout") || msg.contains("connect timed out") || msg.contains("refused"))) {
                status = StorageStatus.UNAVAILABLE;
            }
            log.warn("[STORAGE] MinIO health check failed\nstatus={}\nendpoint={}\nreason={}", status, endpoint, msg);
            return status;
        }
    }

    @Override
    public String uploadFile(String fileName, String contentType, long contentLength, InputStream inputStream) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            String fileUrl = endpoint + "/" + bucketName + "/" + fileName;
            log.info("Successfully uploaded file {} to S3 bucket. URL: {}", fileName, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file {} to S3: {}", fileName, e.getMessage());
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadFile(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (Exception e) {
            log.error("Failed to download file from S3. URL: {}. Error: {}", fileUrl, e.getMessage());
            throw new RuntimeException("File download failed", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file with key {} from S3", key);
        } catch (Exception e) {
            log.error("Failed to delete file from S3. URL: {}. Error: {}", fileUrl, e.getMessage());
            throw new RuntimeException("File deletion failed", e);
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        String prefix = bucketName + "/";
        int index = fileUrl.indexOf(prefix);
        if (index != -1) {
            return fileUrl.substring(index + prefix.length());
        }
        return fileUrl;
    }
}

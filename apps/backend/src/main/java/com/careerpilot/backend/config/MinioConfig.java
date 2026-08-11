package com.careerpilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class MinioConfig {

    @Value("${storage.s3.endpoint}")
    private String endpoint;

    @Value("${storage.s3.access-key}")
    private String accessKey;

    @Value("${storage.s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        String key = (accessKey == null || accessKey.trim().isEmpty()) ? "dummy" : accessKey;
        String secret = (secretKey == null || secretKey.trim().isEmpty()) ? "dummy" : secretKey;
        String ep = (endpoint == null || endpoint.trim().isEmpty()) ? "http://localhost:9000" : endpoint;
        return S3Client.builder()
                .endpointOverride(URI.create(ep))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(key, secret)
                ))
                .region(Region.US_EAST_1) // Region setting is mandatory for AWS SDK builders
                .forcePathStyle(true) // Crucial for direct path-style compatibility on custom S3 / MinIO hosts
                .build();
    }
}

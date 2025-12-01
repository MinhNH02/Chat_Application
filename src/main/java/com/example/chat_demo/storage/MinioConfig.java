package com.example.chat_demo.storage;

import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private boolean secure;

    @Bean
    public MinioClient minioClient() {
        // Parse endpoint: đảm bảo có protocol và dùng IPv4
        String endpointUrl = endpoint;
        log.info("Initial MinIO endpoint from config: {}", endpointUrl);
        
        // Đảm bảo dùng 127.0.0.1 thay vì localhost để tránh IPv6
        if (endpointUrl.contains("localhost")) {
            endpointUrl = endpointUrl.replace("localhost", "127.0.0.1");
            log.debug("Replaced localhost with 127.0.0.1");
        }
        
        // MinIO Java SDK 8.x BẮT BUỘC phải có protocol (http:// hoặc https://)
        // Nếu endpoint không có protocol, thêm http://
        if (!endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")) {
            endpointUrl = "http://" + endpointUrl;
            log.debug("Added http:// protocol to endpoint");
        }
        
        log.info("Creating MinIO client with endpoint: {}, accessKey: {}, secure: {}", 
                endpointUrl, accessKey != null ? "***" : "null", secure);
        
        // Validate inputs
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            throw new IllegalArgumentException("MinIO endpoint cannot be empty");
        }
        if (accessKey == null || accessKey.isEmpty()) {
            throw new IllegalArgumentException("MinIO accessKey cannot be empty");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("MinIO secretKey cannot be empty");
        }
        
        // MinIO Java SDK 8.x cần endpoint có protocol: "http://127.0.0.1:9000"
        try {
            log.debug("Building MinioClient with endpoint: {}, accessKey length: {}", endpointUrl, accessKey.length());
            MinioClient client = MinioClient.builder()
                    .endpoint(endpointUrl)  // Full URL với protocol
                    .credentials(accessKey, secretKey)
                    .build();
            log.info("MinIO client created successfully with endpoint: {}", endpointUrl);
            return client;
        } catch (IllegalArgumentException e) {
            log.error("Invalid MinIO configuration - endpoint: {}, error: {}", endpointUrl, e.getMessage());
            throw new RuntimeException("Invalid MinIO configuration. Endpoint: " + endpointUrl + 
                    ", Error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to create MinIO client with endpoint: {}, error type: {}, message: {}", 
                    endpointUrl, e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to create MinIO client with endpoint: " + endpointUrl + 
                    ". Make sure MinIO is running and accessible. Error: " + e.getMessage(), e);
        }
    }
}


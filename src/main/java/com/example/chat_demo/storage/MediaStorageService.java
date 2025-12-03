package com.example.chat_demo.storage;

import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageService {
    
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    
    /**
     * Upload file lên MinIO
     * @param inputStream File input stream
     * @param originalFilename Tên file gốc
     * @param contentType Content type (image/jpeg, video/mp4, etc.)
     * @param conversationId Conversation ID để organize files
     * @param messageId Message ID
     * @return Object key trong MinIO (để lưu vào database)
     */
    public String uploadFile(InputStream inputStream, String originalFilename, 
                             String contentType, Long conversationId, Long messageId) {
        try {
            // Đảm bảo bucket tồn tại
            ensureBucketExists();
            
            // Tạo object key: conversations/{conversationId}/messages/{messageId}/{uuid}-{filename}
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + "-" + originalFilename;
            String objectKey = String.format("conversations/%d/messages/%d/%s", 
                    conversationId, messageId, uniqueFilename);
            
            // Upload file
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(inputStream, -1, 10485760) // 10MB part size
                    .contentType(contentType)
                    .build()
            );
            
            log.info("Uploaded file to MinIO: {}", objectKey);
            return objectKey;
            
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }
    
    /**
     * Tạo pre-signed URL để truy cập file (có thời hạn)
     * @param objectKey Object key trong MinIO
     * @param expirySeconds Thời gian hết hạn (giây)
     * @return Pre-signed URL
     */
    public String getPresignedUrl(String objectKey, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for object: {}", objectKey, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
    
    /**
     * Download file từ MinIO
     * @param objectKey Object key trong MinIO
     * @return InputStream của file
     */
    public InputStream downloadFile(String objectKey) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", objectKey, e);
            throw new RuntimeException("Failed to download file from MinIO", e);
        }
    }
    
    /**
     * Xóa file từ MinIO
     * @param objectKey Object key trong MinIO
     */
    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build()
            );
            log.info("Deleted file from MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectKey, e);
            throw new RuntimeException("Failed to delete file from MinIO", e);
        }
    }
    
    /**
     * Đảm bảo bucket tồn tại, nếu chưa thì tạo mới
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build()
            );
            
            if (!found) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build()
                );
                log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists", e);
            throw new RuntimeException("Failed to ensure bucket exists", e);
        }
    }
    
    /**
     * Lấy file extension từ filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}






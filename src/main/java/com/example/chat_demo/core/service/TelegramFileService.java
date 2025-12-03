package com.example.chat_demo.core.service;

import com.example.chat_demo.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;

/**
 * TelegramFileService - Service để download file từ Telegram API và upload lên MinIO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramFileService {
    
    private final RestTemplate restTemplate;
    private final MediaStorageService mediaStorageService;
    
    @Value("${platform.telegram.bot-token}")
    private String botToken;
    
    @Value("${platform.telegram.api-url}")
    private String apiUrl;
    
    /**
     * Download file từ Telegram và upload lên MinIO
     * @param fileId Telegram file_id
     * @param conversationId Conversation ID
     * @param messageId Message ID
     * @param filename Tên file (nếu có)
     * @param contentType Content type
     * @return Object key trong MinIO
     */
    public String downloadAndUpload(String fileId, Long conversationId, Long messageId, 
                                     String filename, String contentType) {
        try {
            // 1. Lấy file path từ Telegram API
            String getFileUrl = String.format("%s%s/getFile?file_id=%s", 
                    apiUrl, botToken, fileId);
            
            log.debug("Getting file info from Telegram: {}", getFileUrl);
            var response = restTemplate.getForObject(getFileUrl, java.util.Map.class);
            
            if (response == null || !response.containsKey("result")) {
                log.error("Failed to get file info from Telegram for file_id: {}", fileId);
                return null;
            }
            
            @SuppressWarnings("unchecked")
            var result = (java.util.Map<String, Object>) response.get("result");
            String filePath = (String) result.get("file_path");
            
            if (filePath == null) {
                log.error("File path is null for file_id: {}", fileId);
                return null;
            }
            
            // 2. Download file từ Telegram
            String downloadUrl = String.format("https://api.telegram.org/file/bot%s/%s", 
                    botToken, filePath);
            
            log.debug("Downloading file from Telegram: {}", downloadUrl);
            byte[] fileData = restTemplate.getForObject(downloadUrl, byte[].class);
            
            if (fileData == null) {
                log.error("Failed to download file from Telegram");
                return null;
            }
            
            // 3. Determine filename if not provided
            if (filename == null || filename.isEmpty()) {
                filename = filePath.substring(filePath.lastIndexOf("/") + 1);
            }
            
            // 4. Upload lên MinIO
            try (InputStream inputStream = new java.io.ByteArrayInputStream(fileData)) {
                String objectKey = mediaStorageService.uploadFile(
                    inputStream, 
                    filename, 
                    contentType != null ? contentType : "application/octet-stream",
                    conversationId, 
                    messageId
                );
                log.info("Successfully uploaded file from Telegram to MinIO: {}", objectKey);
                return objectKey;
            }
            
        } catch (Exception e) {
            log.error("Error downloading and uploading file from Telegram", e);
            return null;
        }
    }
}


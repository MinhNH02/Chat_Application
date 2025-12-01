package com.example.chat_demo.omnichannel.connector;

import com.example.chat_demo.common.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * TelegramConnector - Gửi message qua Telegram Bot API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramConnector implements PlatformConnector {
    
    @Value("${platform.telegram.api-url:https://api.telegram.org/bot}")
    private String telegramApiUrl;
    
    @Value("${platform.telegram.bot-token}")
    private String botToken;
    
    private final RestTemplate restTemplate;
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.TELEGRAM;
    }
    
    @Override
    public void sendMessage(String recipientId, String message) {
        try {
            String url = telegramApiUrl + botToken + "/sendMessage";
            log.info("Sending Telegram message to {} via {}", recipientId, url);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", recipientId);
            requestBody.put("text", message);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForObject(url, request, Map.class);
            
            log.info("Sent message to Telegram user: {}", recipientId);
            
        } catch (Exception e) {
            log.error("Error sending message to Telegram user: {}", recipientId, e);
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }

    /**
     * Gửi ảnh qua Telegram bằng URL (MinIO pre-signed URL)
     */
    public void sendPhotoByUrl(String chatId, String photoUrl, String caption) {
        sendMediaByUrl("sendPhoto", "photo", chatId, photoUrl, caption);
    }

    /**
     * Gửi GIF (animation) qua Telegram bằng URL
     */
    public void sendAnimationByUrl(String chatId, String animationUrl, String caption) {
        sendMediaByUrl("sendAnimation", "animation", chatId, animationUrl, caption);
    }

    /**
     * Gửi video qua Telegram bằng URL
     */
    public void sendVideoByUrl(String chatId, String videoUrl, String caption) {
        sendMediaByUrl("sendVideo", "video", chatId, videoUrl, caption);
    }

    /**
     * Gửi file (document) qua Telegram bằng URL
     */
    public void sendDocumentByUrl(String chatId, String fileUrl, String caption) {
        sendMediaByUrl("sendDocument", "document", chatId, fileUrl, caption);
    }

    private void sendMediaByUrl(String method, String fieldName, String chatId, String fileUrl, String caption) {
        try {
            String url = telegramApiUrl + botToken + "/" + method;
            log.info("Sending Telegram {} to {} via {} with url={}", method, chatId, url, fileUrl);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put(fieldName, fileUrl);
            if (caption != null && !caption.isBlank()) {
                requestBody.put("caption", caption);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject(url, request, Map.class);

            log.info("Sent {} to Telegram user: {}", method, chatId);
        } catch (Exception e) {
            log.error("Error sending {} to Telegram user {} with url {}", method, chatId, fileUrl, e);
            throw new RuntimeException("Failed to send Telegram media: " + method, e);
        }
    }

    /**
     * Gửi media qua Telegram bằng multipart/form-data với bytes (dùng cho môi trường local,
     * khi Telegram không truy cập được URL nội bộ như 127.0.0.1).
     */
    public void sendPhotoBytes(String chatId, byte[] bytes, String filename, String mimeType, String caption) {
        sendMediaBytes("sendPhoto", "photo", chatId, bytes, filename, mimeType, caption);
    }

    public void sendAnimationBytes(String chatId, byte[] bytes, String filename, String mimeType, String caption) {
        sendMediaBytes("sendAnimation", "animation", chatId, bytes, filename, mimeType, caption);
    }

    public void sendVideoBytes(String chatId, byte[] bytes, String filename, String mimeType, String caption) {
        sendMediaBytes("sendVideo", "video", chatId, bytes, filename, mimeType, caption);
    }

    public void sendDocumentBytes(String chatId, byte[] bytes, String filename, String mimeType, String caption) {
        sendMediaBytes("sendDocument", "document", chatId, bytes, filename, mimeType, caption);
    }

    private void sendMediaBytes(String method, String fieldName, String chatId,
                                byte[] bytes, String filename, String mimeType, String caption) {
        try {
            String url = telegramApiUrl + botToken + "/" + method;
            log.info("Sending Telegram {} (multipart) to {} via {}", method, chatId, url);

            MediaType mediaType = mimeType != null
                    ? MediaType.parseMediaType(mimeType)
                    : MediaType.APPLICATION_OCTET_STREAM;

            // Tạo resource giữ bytes + filename
            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "file";
                }
            };

            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId);
            body.add(fieldName, new HttpEntity<>(fileResource, createFileHeaders(mediaType)));
            if (caption != null && !caption.isBlank()) {
                body.add("caption", caption);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, Map.class);

            log.info("Sent {} (multipart) to Telegram user: {}", method, chatId);
        } catch (Exception e) {
            log.error("Error sending {} (multipart) to Telegram user {}", method, chatId, e);
            throw new RuntimeException("Failed to send Telegram media bytes: " + method, e);
        }
    }

    private HttpHeaders createFileHeaders(MediaType mediaType) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaType);
        return fileHeaders;
    }
}


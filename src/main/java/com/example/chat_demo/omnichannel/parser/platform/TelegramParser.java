package com.example.chat_demo.omnichannel.parser.platform;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.core.model.UnifiedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * TelegramParser - Parse webhook data từ Telegram
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramParser {
    
    private final ObjectMapper objectMapper;
    
    public UnifiedMessage parse(Object rawData) {
        try {
            Map<String, Object> data = objectMapper.convertValue(rawData, Map.class);
            Map<String, Object> message = (Map<String, Object>) data.get("message");
            
            if (message == null) {
                log.warn("Telegram webhook without message field");
                return null;
            }
            
            Map<String, Object> from = (Map<String, Object>) message.get("from");
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            
            if (from == null) {
                log.warn("Telegram message without from field");
                return null;
            }
            
            Long userId = getLongValue(from, "id");
            Long messageId = getLongValue(message, "message_id");
            Long date = getLongValue(message, "date");
            String text = getStringValue(message, "text");
            
            // Parse attachments (photo, animation, video, document, audio, voice)
            String messageType = "text";
            String attachmentUrl = null;
            String attachmentType = null;
            String attachmentFilename = null;
            Long attachmentSize = null;
            
            // Ưu tiên animation (GIF động)
            if (message.containsKey("animation")) {
                // Telegram GIF animation
                messageType = "image";          // FE có thể treat như ảnh/GIF
                attachmentType = "image";

                Map<String, Object> animation = (Map<String, Object>) message.get("animation");
                if (animation != null) {
                    attachmentUrl = getStringValue(animation, "file_id");
                    attachmentFilename = getStringValue(animation, "file_name");
                    attachmentSize = getLongValue(animation, "file_size");
                }

                if (text == null || text.isEmpty()) {
                    text = "[GIF]";
                }
            }
            // Check for photo
            else if (message.containsKey("photo")) {
                messageType = "image";
                attachmentType = "image";
                // Telegram returns array of photos, get the largest one
                java.util.List<Map<String, Object>> photos = (java.util.List<Map<String, Object>>) message.get("photo");
                if (photos != null && !photos.isEmpty()) {
                    Map<String, Object> largestPhoto = photos.get(photos.size() - 1);
                    String fileId = getStringValue(largestPhoto, "file_id");
                    attachmentUrl = fileId; // Will be used to download from Telegram API
                    attachmentSize = getLongValue(largestPhoto, "file_size");
                }
                if (text == null || text.isEmpty()) {
                    text = "[Photo]";
                }
            }
            // Check for video
            else if (message.containsKey("video")) {
                messageType = "video";
                attachmentType = "video";
                Map<String, Object> video = (Map<String, Object>) message.get("video");
                if (video != null) {
                    attachmentUrl = getStringValue(video, "file_id");
                    attachmentFilename = getStringValue(video, "file_name");
                    attachmentSize = getLongValue(video, "file_size");
                }
                if (text == null || text.isEmpty()) {
                    text = "[Video]";
                }
            }
            // Check for document (có thể là ảnh gửi dạng file, PDF, ZIP, ...)
            else if (message.containsKey("document")) {
                Map<String, Object> document = (Map<String, Object>) message.get("document");
                String mimeType = document != null ? getStringValue(document, "mime_type") : null;
                String fileName = document != null ? getStringValue(document, "file_name") : null;

                // Phân loại document là image nếu mime hoặc đuôi file là ảnh
                boolean isImageDoc =
                        (mimeType != null && mimeType.startsWith("image/")) ||
                        (fileName != null && fileName.toLowerCase().matches(".*\\.(jpe?g|png|gif|webp)$"));

                messageType = isImageDoc ? "image" : "document";
                attachmentType = isImageDoc ? "image" : "document";

                if (document != null) {
                    attachmentUrl = getStringValue(document, "file_id");
                    attachmentFilename = fileName;
                    attachmentSize = getLongValue(document, "file_size");
                }
                if (text == null || text.isEmpty()) {
                    text = isImageDoc ? "[Photo]" : "[Document]";
                }
            }
            // Check for audio
            else if (message.containsKey("audio")) {
                messageType = "audio";
                attachmentType = "audio";
                Map<String, Object> audio = (Map<String, Object>) message.get("audio");
                if (audio != null) {
                    attachmentUrl = getStringValue(audio, "file_id");
                    attachmentFilename = getStringValue(audio, "file_name");
                    attachmentSize = getLongValue(audio, "file_size");
                }
                if (text == null || text.isEmpty()) {
                    text = "[Audio]";
                }
            }
            // Check for voice
            else if (message.containsKey("voice")) {
                messageType = "voice";
                attachmentType = "audio";
                Map<String, Object> voice = (Map<String, Object>) message.get("voice");
                if (voice != null) {
                    attachmentUrl = getStringValue(voice, "file_id");
                    attachmentSize = getLongValue(voice, "file_size");
                }
                if (text == null || text.isEmpty()) {
                    text = "[Voice message]";
                }
            }
            // Nếu không có text và không có attachment
            else if (text == null || text.isEmpty()) {
                text = "[Non-text message]";
            }
            
            return UnifiedMessage.builder()
                .channelType(ChannelType.TELEGRAM)
                .platformUserId(String.valueOf(userId))
                .platformMessageId(String.valueOf(messageId))
                .content(text)
                .messageType(messageType)
                .timestamp(Instant.ofEpochSecond(date != null ? date : System.currentTimeMillis() / 1000)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime())
                .username(getStringValue(from, "username"))
                .firstName(getStringValue(from, "first_name"))
                .lastName(getStringValue(from, "last_name"))
                .attachmentUrl(attachmentUrl)  // Telegram file_id, sẽ download sau
                .attachmentType(attachmentType)
                .attachmentFilename(attachmentFilename)
                .attachmentSize(attachmentSize)
                .rawData(rawData)
                .build();
                
        } catch (Exception e) {
            log.error("Error parsing Telegram webhook", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    @SuppressWarnings("unchecked")
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}


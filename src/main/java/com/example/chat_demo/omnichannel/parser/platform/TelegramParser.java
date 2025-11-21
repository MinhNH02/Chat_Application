package com.example.chat_demo.omnichannel.parser.platform;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
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
            
            // Nếu không có text, có thể là sticker, photo, etc.
            if (text == null || text.isEmpty()) {
                text = "[Non-text message]"; // Hoặc parse các loại message khác
            }
            
            return UnifiedMessage.builder()
                .channelType(ChannelType.TELEGRAM)
                .platformUserId(String.valueOf(userId))
                .platformMessageId(String.valueOf(messageId))
                .content(text)
                .messageType("text")
                .timestamp(Instant.ofEpochSecond(date != null ? date : System.currentTimeMillis() / 1000)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime())
                .username(getStringValue(from, "username"))
                .firstName(getStringValue(from, "first_name"))
                .lastName(getStringValue(from, "last_name"))
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


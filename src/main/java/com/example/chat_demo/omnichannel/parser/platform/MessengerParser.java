package com.example.chat_demo.omnichannel.parser.platform;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * MessengerParser - Parse webhook data từ Facebook Messenger
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessengerParser {

    private final ObjectMapper objectMapper;

    public UnifiedMessage parse(Object rawData) {
        try {
            Map<String, Object> data = objectMapper.convertValue(rawData, Map.class);
            List<Map<String, Object>> entries = (List<Map<String, Object>>) data.get("entry");

            if (entries == null || entries.isEmpty()) {
                log.warn("Messenger webhook without entry");
                return null;
            }

            Map<String, Object> entry = entries.get(0);
            List<Map<String, Object>> messaging = (List<Map<String, Object>>) entry.get("messaging");

            if (messaging == null || messaging.isEmpty()) {
                log.warn("Messenger webhook without messaging");
                return null;
            }

            Map<String, Object> messageEvent = messaging.get(0);
            Map<String, Object> sender = (Map<String, Object>) messageEvent.get("sender");
            Map<String, Object> message = (Map<String, Object>) messageEvent.get("message");

            if (sender == null || message == null) {
                log.warn("Messenger message missing sender or message");
                return null;
            }

            String psid = getStringValue(sender, "id");
            String messageId = getStringValue(message, "mid");
            String text = getStringValue(message, "text");
            Long timestamp = getLongValue(messageEvent, "timestamp");

            LocalDateTime receivedAt = timestamp != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                    : LocalDateTime.now();

            return UnifiedMessage.builder()
                    .channelType(ChannelType.MESSENGER)
                    .platformUserId(psid)
                    .platformMessageId(messageId)
                    .content(text != null ? text : "[Unsupported message type]")
                    .messageType("text")
                    .timestamp(receivedAt)
                    .rawData(rawData)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing Messenger webhook", e);
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}


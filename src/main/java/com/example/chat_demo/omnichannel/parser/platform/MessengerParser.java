package com.example.chat_demo.omnichannel.parser.platform;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.core.model.UnifiedMessage;
import com.example.chat_demo.core.service.MessengerUserProfileService;
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
    private final MessengerUserProfileService userProfileService;

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

            // Lấy user profile từ Graph API (firstName, lastName)
            MessengerUserProfileService.UserProfile profile = userProfileService.getUserProfile(psid);
            String firstName = profile != null ? profile.firstName() : null;
            String lastName = profile != null ? profile.lastName() : null;

            // Xử lý message type và content
            String messageType = "text";
            String content = text;
            
            // Kiểm tra nếu có attachments (images, files, etc.)
            Map<String, Object> attachments = (Map<String, Object>) message.get("attachments");
            if (attachments != null && !attachments.isEmpty()) {
                List<Map<String, Object>> attachmentList = (List<Map<String, Object>>) attachments.get("data");
                if (attachmentList != null && !attachmentList.isEmpty()) {
                    Map<String, Object> firstAttachment = attachmentList.get(0);
                    String attachmentType = getStringValue(firstAttachment, "type");
                    messageType = attachmentType != null ? attachmentType : "attachment";
                    
                    if (content == null || content.isEmpty()) {
                        content = "[Message with " + messageType + "]";
                    }
                }
            }
            
            if (content == null || content.isEmpty()) {
                content = "[Unsupported message type]";
            }

            return UnifiedMessage.builder()
                    .channelType(ChannelType.MESSENGER)
                    .platformUserId(psid)
                    .platformMessageId(messageId)
                    .content(content)
                    .messageType(messageType)
                    .timestamp(receivedAt)
                    .firstName(firstName)
                    .lastName(lastName)
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


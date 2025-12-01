package com.example.chat_demo.api.mapper;

import com.example.chat_demo.api.dto.MessageDto;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MessageMapper - chuyển Message entity sang DTO phục vụ API/WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final MediaStorageService mediaStorageService;

    public MessageDto toDto(Message msg) {
        MessageDto dto = new MessageDto();
        dto.setId(msg.getId());
        dto.setContent(msg.getContent());
        dto.setMessageType(msg.getMessageType());

        if (msg.getDirection() != null) {
            dto.setDirection(msg.getDirection().name());
        }
        if (msg.getStatus() != null) {
            dto.setStatus(msg.getStatus().name());
        }

        dto.setReceivedAt(msg.getReceivedAt());
        dto.setSentAt(msg.getSentAt());

        if (msg.getUser() != null) {
            dto.setUserId(msg.getUser().getId());
            String firstName = msg.getUser().getFirstName();
            String lastName = msg.getUser().getLastName();
            String fallback = msg.getUser().getUsername() != null ? msg.getUser().getUsername() : "Unknown";
            dto.setUserName(
                    ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim()
                            .isEmpty() ? fallback :
                            ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim()
            );
        }
        
        // Map attachment fields
        dto.setAttachmentType(msg.getAttachmentType());
        dto.setAttachmentFilename(msg.getAttachmentFilename());
        dto.setAttachmentSize(msg.getAttachmentSize());
        
        // Tự động tạo pre-signed URL nếu có attachment
        if (msg.getAttachmentUrl() != null && !msg.getAttachmentUrl().isEmpty()) {
            try {
                // Tạo pre-signed URL (expiry: 1 hour)
                String presignedUrl = mediaStorageService.getPresignedUrl(msg.getAttachmentUrl(), 3600);
                dto.setAttachmentUrl(presignedUrl);
                log.debug("Generated pre-signed URL for message {} attachment", msg.getId());
            } catch (Exception e) {
                log.error("Failed to generate pre-signed URL for message {} attachment: {}", 
                    msg.getId(), msg.getAttachmentUrl(), e);
                // Fallback: trả về object key nếu không tạo được pre-signed URL
                dto.setAttachmentUrl(msg.getAttachmentUrl());
            }
        } else {
            dto.setAttachmentUrl(null);
        }

        return dto;
    }
}


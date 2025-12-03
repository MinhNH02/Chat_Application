package com.example.chat_demo.core.bus;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.core.model.UnifiedMessage;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.MessageRepository;
import com.example.chat_demo.core.realtime.RealtimeMessagePublisher;
import com.example.chat_demo.core.service.ConversationStateService;
import com.example.chat_demo.core.service.TelegramFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * OmnichannelMessageBus - Lưu messages vào DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmnichannelMessageBus {
    
    private final MessageRepository messageRepository;
    private final ConversationStateService conversationStateService;
    private final RealtimeMessagePublisher realtimeMessagePublisher;
    private final TelegramFileService telegramFileService;
    
    /**
     * Lưu inbound message vào DB
     * @param unifiedMessage Message đã được chuẩn hóa
     * @param user User entity
     * @return Message entity đã lưu
     */
    @Transactional
    public Message saveInboundMessage(UnifiedMessage unifiedMessage, User user) {
        // Lấy hoặc tạo conversation
        Conversation conversation = conversationStateService.getOrCreateActiveConversation(user);
        log.debug("Inbound message will use conversation {}", conversation.getId());
        
        // Lưu channel ID vào conversation nếu có (dùng cho Discord)
        if (unifiedMessage.getChannelId() != null && !unifiedMessage.getChannelId().isBlank()) {
            conversation.setChannelId(unifiedMessage.getChannelId());
            log.debug("Set channel ID {} for conversation {}", unifiedMessage.getChannelId(), conversation.getId());
        }
        
        // Tạo message entity
        Message message = new Message();
        message.setUser(user);
        message.setConversation(conversation);
        message.setPlatformMessageId(unifiedMessage.getPlatformMessageId());
        message.setContent(unifiedMessage.getContent());
        message.setMessageType(unifiedMessage.getMessageType());
        message.setReceivedAt(unifiedMessage.getTimestamp());
        message.setDirection(Message.MessageDirection.INBOUND);
        message.setStatus(Message.MessageStatus.DELIVERED);
        
        // Xử lý attachment nếu có (download từ platform và upload lên MinIO)
        Message savedMessage;
        if (unifiedMessage.getAttachmentUrl() != null && 
            !unifiedMessage.getAttachmentUrl().isEmpty()) {
            
            // Lưu message tạm để có ID
            savedMessage = messageRepository.save(message);
            
            // Download và upload file lên MinIO
            String minioObjectKey = null;
            if (unifiedMessage.getChannelType() == ChannelType.TELEGRAM) {
                minioObjectKey = telegramFileService.downloadAndUpload(
                    unifiedMessage.getAttachmentUrl(), // Telegram file_id
                    conversation.getId(),
                    savedMessage.getId(),
                    unifiedMessage.getAttachmentFilename(),
                    getContentTypeFromAttachmentType(unifiedMessage.getAttachmentType())
                );
            }
            
            // Cập nhật message với MinIO object key
            if (minioObjectKey != null) {
                savedMessage.setAttachmentUrl(minioObjectKey);
                savedMessage.setAttachmentType(unifiedMessage.getAttachmentType());
                savedMessage.setAttachmentFilename(unifiedMessage.getAttachmentFilename());
                savedMessage.setAttachmentSize(unifiedMessage.getAttachmentSize());
                log.info("Attachment uploaded to MinIO: {}", minioObjectKey);
                // Save lại để cập nhật attachment info
                savedMessage = messageRepository.save(savedMessage);
            } else {
                log.warn("Failed to upload attachment to MinIO for message {}", savedMessage.getId());
            }
        } else {
            // Không có attachment, lưu message bình thường
            savedMessage = messageRepository.save(message);
        }
        log.info("Inbound message saved with id {}", savedMessage.getId());
        
        // Cập nhật conversation
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationStateService.updateConversation(conversation);
        
        log.debug("Saved inbound message: {} for user: {}", 
            savedMessage.getId(), user.getPlatformUserId());

        realtimeMessagePublisher.publish(savedMessage);
        
        return savedMessage;
    }
    
    /**
     * Lưu outbound message vào DB
     */
    @Transactional
    public Message saveOutboundMessage(String content, User user, Conversation conversation) {
        Message message = new Message();
        message.setUser(user);
        message.setConversation(conversation);
        message.setContent(content);
        message.setMessageType("text");
        message.setSentAt(LocalDateTime.now());
        message.setDirection(Message.MessageDirection.OUTBOUND);
        message.setStatus(Message.MessageStatus.PENDING);
        
        Message savedMessage = messageRepository.save(message);
        log.info("Outbound message saved with id {} for conversation {}", savedMessage.getId(), conversation.getId());
        
        // Cập nhật conversation
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationStateService.updateConversation(conversation);
        realtimeMessagePublisher.publish(savedMessage);
        
        return savedMessage;
    }
    
    /**
     * Map attachment type sang content type
     */
    private String getContentTypeFromAttachmentType(String attachmentType) {
        if (attachmentType == null) {
            return "application/octet-stream";
        }
        return switch (attachmentType.toLowerCase()) {
            case "image" -> "image/jpeg";
            case "video" -> "video/mp4";
            case "audio" -> "audio/mpeg";
            case "document" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}


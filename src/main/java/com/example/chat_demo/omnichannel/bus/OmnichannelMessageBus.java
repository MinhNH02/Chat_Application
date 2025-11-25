package com.example.chat_demo.omnichannel.bus;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import com.example.chat_demo.core.repository.MessageRepository;
import com.example.chat_demo.core.service.ConversationStateService;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
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
        
        // Lưu message
        Message savedMessage = messageRepository.save(message);
        log.info("Inbound message saved with id {}", savedMessage.getId());
        
        // Cập nhật conversation
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationStateService.updateConversation(conversation);
        
        log.debug("Saved inbound message: {} for user: {}", 
            savedMessage.getId(), user.getPlatformUserId());
        
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
        
        return savedMessage;
    }
}


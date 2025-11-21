package com.example.chat_demo.omnichannel.router;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.service.ConversationStateService;
import com.example.chat_demo.core.service.UserRegistryService;
import com.example.chat_demo.omnichannel.bus.OmnichannelMessageBus;
import com.example.chat_demo.omnichannel.connector.ConnectorFactory;
import com.example.chat_demo.omnichannel.connector.PlatformConnector;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OmnichannelRouter - Điều phối xử lý message và auto-reply
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmnichannelRouter {
    
    private final UserRegistryService userRegistryService;
    private final OmnichannelMessageBus messageBus;
    private final ConversationStateService conversationStateService;
    private final ConnectorFactory connectorFactory;
    
    @Value("${omnichannel.auto-reply.enabled:true}")
    private boolean autoReplyEnabled;
    
    @Value("${omnichannel.auto-reply.welcome-message:Xin chào! Chúng tôi sẽ phản hồi bạn sớm nhất có thể.}")
    private String welcomeMessage;
    
    /**
     * Xử lý message từ user
     * @param unifiedMessage Message đã được chuẩn hóa
     */
    @Transactional
    public void routeMessage(UnifiedMessage unifiedMessage) {
        log.info("Routing message from platform {} user {}", unifiedMessage.getChannelType(), unifiedMessage.getPlatformUserId());
        // 1. Kiểm tra có phải user mới không (trước khi register)
        boolean isNewUser = userRegistryService.isNewUser(unifiedMessage);
        
        // 2. Đăng ký hoặc lấy user
        User user = userRegistryService.registerOrGetUser(unifiedMessage);
        
        // 3. Lưu message vào DB
        messageBus.saveInboundMessage(unifiedMessage, user);
        log.info("Saved inbound message {} for user {}", unifiedMessage.getPlatformMessageId(), user.getId());
        
        // 4. Nếu là user mới, gửi tin nhắn chào mừng
        if (isNewUser && autoReplyEnabled) {
            log.info("User {} is new, sending welcome message", user.getId());
            sendWelcomeMessage(user);
        }
    }
    
    /**
     * Gửi tin nhắn chào mừng cho user mới
     */
    private void sendWelcomeMessage(User user) {
        try {
            Conversation conversation = conversationStateService.getOrCreateActiveConversation(user);
            log.debug("Using conversation {} for welcome message", conversation.getId());
            
            // Lấy connector tương ứng
            PlatformConnector connector = connectorFactory.getConnector(user.getChannelType());
            
            // Gửi message
            connector.sendMessage(user.getPlatformUserId(), welcomeMessage);
            log.info("Welcome message dispatched via {} connector", user.getChannelType());
            
            // Lưu message vào DB
            messageBus.saveOutboundMessage(welcomeMessage, user, conversation);
            
            log.info("Sent welcome message to new user: {} on platform {}", 
                user.getPlatformUserId(), user.getChannelType());
                
        } catch (Exception e) {
            log.error("Failed to send welcome message", e);
        }
    }
}


package com.example.chat_demo.core.service;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ConversationStateService - Quản lý state của conversations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStateService {
    
    private final ConversationRepository conversationRepository;
    
    /**
     * Lấy hoặc tạo conversation đang mở cho user
     */
    @Transactional
    public Conversation getOrCreateActiveConversation(User user) {
        return conversationRepository
            .findByUserAndStatus(user, "OPEN")
            .orElseGet(() -> {
                Conversation newConversation = new Conversation();
                newConversation.setUser(user);
                newConversation.setStatus("OPEN");
                newConversation.setStartedAt(LocalDateTime.now());
                newConversation.setLastMessageAt(LocalDateTime.now());
                
                Conversation saved = conversationRepository.save(newConversation);
                log.debug("Created new conversation: {} for user: {}", 
                    saved.getId(), user.getPlatformUserId());
                
                return saved;
            });
    }
    
    /**
     * Cập nhật conversation
     */
    @Transactional
    public Conversation updateConversation(Conversation conversation) {
        return conversationRepository.save(conversation);
    }
    
    /**
     * Đóng conversation
     */
    @Transactional
    public void closeConversation(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setStatus("CLOSED");
            conversation.setClosedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        });
    }
}


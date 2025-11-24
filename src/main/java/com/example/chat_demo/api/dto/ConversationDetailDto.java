package com.example.chat_demo.api.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ConversationDetailDto - Response cho conversation detail kèm messages
 */
@Data
public class ConversationDetailDto {
    // Conversation info
    private Long id;
    private Long userId;
    private String userName;
    private String userPlatformId;
    private String channelType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    
    // Messages (50 tin nhắn gần nhất hoặc khi scroll lên)
    private List<MessageDto> messages;
    private boolean hasMore;           // Còn tin nhắn cũ hơn không
    private Long oldestMessageId;      // ID tin nhắn cũ nhất trong response
    private Long newestMessageId;       // ID tin nhắn mới nhất trong response
    private int totalCount;            // Tổng số tin nhắn trong conversation
}


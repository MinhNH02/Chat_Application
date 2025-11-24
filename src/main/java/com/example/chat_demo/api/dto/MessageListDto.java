package com.example.chat_demo.api.dto;

import lombok.Data;
import java.util.List;

/**
 * MessageListDto - Response cho infinite scroll chat history
 */
@Data
public class MessageListDto {
    private List<MessageDto> messages;
    private boolean hasMore;           // Còn tin nhắn cũ hơn không
    private Long oldestMessageId;      // ID tin nhắn cũ nhất trong response
    private Long newestMessageId;      // ID tin nhắn mới nhất trong response
    private int totalCount;            // Tổng số tin nhắn trong conversation
}


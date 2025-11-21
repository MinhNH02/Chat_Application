package com.example.chat_demo.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userPlatformId;
    private String channelType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
}


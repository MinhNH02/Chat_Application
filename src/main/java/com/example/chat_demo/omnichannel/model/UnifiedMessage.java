package com.example.chat_demo.omnichannel.model;

import com.example.chat_demo.common.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * UnifiedMessage - Message đã được chuẩn hóa từ các platform khác nhau
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedMessage {
    private ChannelType channelType;
    private String platformUserId;      // chat_id / psid / zalo_user_id
    private String platformMessageId;   // message_id từ platform
    private String content;
    private String messageType;         // text, image, file, etc.
    private LocalDateTime timestamp;
    
    // User info
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    // Raw data từ platform (để debug)
    private Object rawData;
}


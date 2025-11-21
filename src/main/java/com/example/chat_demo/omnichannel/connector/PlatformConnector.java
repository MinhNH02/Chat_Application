package com.example.chat_demo.omnichannel.connector;

import com.example.chat_demo.common.ChannelType;

/**
 * PlatformConnector - Interface cho việc gửi message đến các platform
 */
public interface PlatformConnector {
    
    /**
     * Xác định platform này xử lý channel nào
     */
    ChannelType getChannelType();
    
    /**
     * Gửi message đến user trên platform
     * @param recipientId ID của user trên platform (chat_id / psid / zalo_user_id)
     * @param message Nội dung message
     */
    void sendMessage(String recipientId, String message);
}


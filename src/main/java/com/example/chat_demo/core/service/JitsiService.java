package com.example.chat_demo.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * JitsiService - Service để tạo Jitsi room ID và URL
 */
@Slf4j
@Service
public class JitsiService {
    
    @Value("${jitsi.base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;
    
    @Value("${jitsi.room-prefix:support-call}")
    private String roomPrefix;
    
    /**
     * Tạo Jitsi room ID unique
     * Format: support-call-{conversationId}-{uuid}
     * 
     * @param conversationId Conversation ID
     * @return Room ID unique
     */
    public String generateRoomId(Long conversationId) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String roomId = String.format("%s-%d-%s", roomPrefix, conversationId, uuid);
        log.debug("Generated Jitsi room ID: {} for conversation {}", roomId, conversationId);
        return roomId;
    }
    
    /**
     * Tạo Jitsi room URL từ room ID
     * 
     * @param roomId Room ID
     * @return Full Jitsi room URL
     */
    public String buildRoomUrl(String roomId) {
        // Đảm bảo base URL có protocol
        String base = jitsiBaseUrl.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
            log.debug("Added https:// protocol to Jitsi base URL");
        }
        
        // Remove trailing slash
        base = base.replaceAll("/$", "");
        
        String url = base + "/" + roomId;
        log.info("Built Jitsi room URL: {} (roomId: {})", url, roomId);
        return url;
    }
    
    /**
     * Validate room ID format (chỉ cho phép alphanumeric, dash, underscore)
     * 
     * @param roomId Room ID để validate
     * @return true nếu valid, false nếu không
     */
    public boolean isValidRoomId(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return false;
        }
        // Jitsi room ID chỉ cho phép: a-z, A-Z, 0-9, -, _
        boolean valid = roomId.matches("^[a-zA-Z0-9-_]+$");
        if (!valid) {
            log.warn("Invalid Jitsi room ID format: {}", roomId);
        }
        return valid;
    }
}


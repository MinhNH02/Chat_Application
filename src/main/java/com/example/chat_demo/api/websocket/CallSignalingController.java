package com.example.chat_demo.api.websocket;

import com.example.chat_demo.core.model.Call;
import com.example.chat_demo.core.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * CallSignalingController - WebSocket controller cho call signaling
 * Xử lý các event: join call, end call
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CallSignalingController {
    
    private final CallService callService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Staff/Customer join call → notify qua WebSocket
     * Destination: /app/call/join
     * Payload: { "roomId": "support-call-123-456", "userId": "staff" }
     */
    @MessageMapping("/call/join")
    public void handleJoinCall(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        
        log.info("User {} joined call room {}", userId, roomId);
        
        try {
            Call call = callService.findByRoomId(roomId);
            
            // Update status nếu cần
            if (call.getStatus() == Call.CallStatus.INITIATED || call.getStatus() == Call.CallStatus.RINGING) {
                callService.updateCallStatus(call.getId(), Call.CallStatus.ACTIVE);
            }
            
            // Broadcast đến conversation
            Map<String, Object> response = new HashMap<>();
            response.put("type", "call_joined");
            response.put("roomId", roomId);
            response.put("userId", userId);
            response.put("status", "ACTIVE");
            response.put("callId", call.getId());
            
            String destination = "/topic/conversations/" + call.getConversation().getId() + "/call";
            messagingTemplate.convertAndSend(destination, response);
            
            log.info("Broadcasted call_joined event to {}", destination);
            
        } catch (Exception e) {
            log.error("Error handling join call for room {}", roomId, e);
        }
    }
    
    /**
     * End call
     * Destination: /app/call/end
     * Payload: { "roomId": "support-call-123-456", "userId": "staff" }
     */
    @MessageMapping("/call/end")
    public void handleEndCall(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String userId = payload.get("userId");
        
        log.info("User {} ended call room {}", userId, roomId);
        
        try {
            Call call = callService.findByRoomId(roomId);
            callService.updateCallStatus(call.getId(), Call.CallStatus.ENDED);
            
            Map<String, Object> response = new HashMap<>();
            response.put("type", "call_ended");
            response.put("roomId", roomId);
            response.put("userId", userId);
            response.put("callId", call.getId());
            
            String destination = "/topic/conversations/" + call.getConversation().getId() + "/call";
            messagingTemplate.convertAndSend(destination, response);
            
            log.info("Broadcasted call_ended event to {}", destination);
            
        } catch (Exception e) {
            log.error("Error handling end call for room {}", roomId, e);
        }
    }
}


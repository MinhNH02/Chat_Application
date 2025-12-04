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
        
        log.info("[CALL WS] ===== JOIN CALL =====");
        log.info("[CALL WS] User {} joined call room {}", userId, roomId);
        log.info("[CALL WS] Payload: {}", payload);
        
        try {
            Call call = callService.findByRoomId(roomId);
            log.info("[CALL WS] Call found - ID: {}, CurrentStatus: {}, RoomId: {}, ConversationId: {}", 
                call.getId(), call.getStatus(), roomId, call.getConversation().getId());
            
            // Update status nếu cần
            if (call.getStatus() == Call.CallStatus.INITIATED || call.getStatus() == Call.CallStatus.RINGING) {
                log.info("[CALL WS] Updating status from {} to ACTIVE", call.getStatus());
                call = callService.updateCallStatus(call.getId(), Call.CallStatus.ACTIVE);
                log.info("[CALL WS] Status updated - ID: {}, NewStatus: {}", call.getId(), call.getStatus());
            } else {
                log.info("[CALL WS] Status is already {}, no update needed", call.getStatus());
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
            
            log.info("[CALL WS] Broadcasted call_joined event to {} - CallId: {}, Status: ACTIVE", 
                destination, call.getId());
        log.info("[CALL WS] ===== JOIN CALL COMPLETE =====");
            
        } catch (Exception e) {
            log.error("[CALL WS] Error handling join call for room {}", roomId, e);
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
        
        log.info("[CALL WS] ===== END CALL =====");
        log.info("[CALL WS] User {} ended call room {}", userId, roomId);
        log.info("[CALL WS] Payload: {}", payload);
        
        try {
            Call call = callService.findByRoomId(roomId);
            log.info("[CALL WS] Call found - ID: {}, CurrentStatus: {}, RoomId: {}, ConversationId: {}", 
                call.getId(), call.getStatus(), roomId, call.getConversation().getId());
            
            Call.CallStatus oldStatus = call.getStatus();
            call = callService.updateCallStatus(call.getId(), Call.CallStatus.ENDED);
            log.info("[CALL WS] Status updated - ID: {}, OldStatus: {}, NewStatus: ENDED", 
                call.getId(), oldStatus);
            
            Map<String, Object> response = new HashMap<>();
            response.put("type", "call_ended");
            response.put("roomId", roomId);
            response.put("userId", userId);
            response.put("callId", call.getId());
            
            String destination = "/topic/conversations/" + call.getConversation().getId() + "/call";
            messagingTemplate.convertAndSend(destination, response);
            
            log.info("[CALL WS] Broadcasted call_ended event to {} - CallId: {}, Status: ENDED", 
                destination, call.getId());
        log.info("[CALL WS] ===== END CALL COMPLETE =====");
            
        } catch (Exception e) {
            log.error("[CALL WS] Error handling end call for room {}", roomId, e);
        }
    }
}


package com.example.chat_demo.api.controller;

import com.example.chat_demo.api.dto.CallDto;
import com.example.chat_demo.api.dto.InitiateCallResponse;
import com.example.chat_demo.core.model.Call;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import com.example.chat_demo.core.service.CallService;
import com.example.chat_demo.omnichannel.connector.ConnectorFactory;
import com.example.chat_demo.omnichannel.connector.PlatformConnector;
import com.example.chat_demo.omnichannel.connector.TelegramConnector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CallApiController - API cho voice/video call với Jitsi
 */
@Slf4j
@RestController
@Tag(name = "Call API", description = "API cho voice/video call với Jitsi")
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@RequiredArgsConstructor
public class CallApiController {
    
    private final CallService callService;
    private final ConversationRepository conversationRepository;
    private final ConnectorFactory connectorFactory;
    
    /**
     * Staff bấm "Gọi khách hàng" → Tạo call và gửi link qua Telegram
     */
    @Operation(summary = "Bắt đầu cuộc gọi", 
              description = "Staff bấm gọi → Backend tạo Jitsi room và gửi link cho customer qua Telegram")
    @PostMapping("/conversations/{id}/calls/initiate")
    public ResponseEntity<InitiateCallResponse> initiateCall(@PathVariable Long id) {
        log.info("[CALL API] ===== INITIATE CALL =====");
        log.info("[CALL API] POST /api/conversations/{}/calls/initiate", id);
        
        if (id == null || id <= 0) {
            log.error("[CALL API] Invalid conversation ID: {}", id);
            throw new IllegalArgumentException("Invalid conversation ID");
        }
        
        // Tạo call (bao gồm tạo Jitsi room)
        Call call = callService.initiateCall(id);
        
        log.info("[CALL API] Call created - ID: {}, Status: {}, RoomId: {}, RoomUrl: {}", 
            call.getId(), call.getStatus(), call.getJitsiRoomId(), call.getJitsiRoomUrl());
        
        // Lấy conversation và user (tối ưu: fetch cùng lúc)
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
        User user = conversation.getUser();
        
        // Gửi notification ASYNC (không block response) - Tối ưu tốc độ
        sendCallNotificationAsync(call, user);
        
        // Build response ngay lập tức (không đợi notification)
        InitiateCallResponse response = new InitiateCallResponse();
        response.setCallId(call.getId());
        response.setJitsiRoomUrl(call.getJitsiRoomUrl());
        response.setJitsiRoomId(call.getJitsiRoomId());
        response.setMessage("Call initiated. Notification is being sent to customer.");
        
        log.info("[CALL API] Response sent - CallId: {}, Status: {}, RoomId: {}", 
            call.getId(), call.getStatus(), call.getJitsiRoomId());
        log.info("[CALL API] ===== INITIATE CALL COMPLETE =====");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gửi notification async để không block response
     * Tối ưu: Response trả về ngay, notification gửi ở background
     */
    @Async("callNotificationExecutor")
    public CompletableFuture<Void> sendCallNotificationAsync(Call call, User user) {
        try {
            String recipientId = user.getPlatformUserId();
            // Hiển thị link trong message (giống Google Meet) + button để mở tab mới
            String messageText = "Bấm để tham gia cuộc gọi:\n" + call.getJitsiRoomUrl();
            
            PlatformConnector connector = connectorFactory.getConnector(user.getChannelType());
            if (connector instanceof TelegramConnector) {
                TelegramConnector telegramConnector = (TelegramConnector) connector;
                telegramConnector.sendMessageWithButton(
                    recipientId,
                    messageText,
                    "Join Call",
                    call.getJitsiRoomUrl()
                );
                log.info("Sent call notification to Telegram user {} with room URL {}", recipientId, call.getJitsiRoomUrl());
            } else {
                // Platform khác: gửi text message với link
                connector.sendMessage(recipientId, messageText);
                log.info("Sent call notification to {} user {} with room URL {}", 
                    user.getChannelType(), recipientId, call.getJitsiRoomUrl());
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send call notification for call {} to user {}", call.getId(), user.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Lấy thông tin call
     */
    @Operation(summary = "Lấy thông tin call", description = "Lấy thông tin cuộc gọi theo ID")
    @GetMapping("/calls/{callId}")
    public ResponseEntity<CallDto> getCall(@PathVariable Long callId) {
        log.info("[CALL API] GET /api/calls/{}", callId);
        
        Call call = callService.findById(callId);
        CallDto dto = toDto(call);
        
        log.info("[CALL API] Call found - ID: {}, Status: {}, RoomId: {}, ConversationId: {}, InitiatedAt: {}, StartedAt: {}, EndedAt: {}", 
            call.getId(), call.getStatus(), call.getJitsiRoomId(), call.getConversation().getId(), 
            call.getInitiatedAt(), call.getStartedAt(), call.getEndedAt());
        
        return ResponseEntity.ok(dto);
    }
    
    /**
     * Lấy call theo room ID (Jitsi room ID)
     */
    @Operation(summary = "Lấy call theo room ID", description = "Lấy thông tin cuộc gọi theo Jitsi room ID")
    @GetMapping("/calls/room/{roomId}")
    public ResponseEntity<CallDto> getCallByRoomId(@PathVariable String roomId) {
        log.info("[CALL API] GET /api/calls/room/{}", roomId);
        
        Call call = callService.findByRoomId(roomId);
        CallDto dto = toDto(call);
        
        log.info("[CALL API] Call found by roomId - ID: {}, Status: {}, RoomId: {}, ConversationId: {}", 
            call.getId(), call.getStatus(), call.getJitsiRoomId(), call.getConversation().getId());
        
        return ResponseEntity.ok(dto);
    }
    
    /**
     * Lấy active call của conversation
     */
    @Operation(summary = "Lấy active call", description = "Lấy cuộc gọi đang active của conversation")
    @GetMapping("/conversations/{id}/calls/active")
    public ResponseEntity<CallDto> getActiveCall(@PathVariable Long id) {
        log.info("[CALL API] GET /api/conversations/{}/calls/active", id);
        
        Call call = callService.getActiveCall(id);
        if (call == null) {
            log.info("[CALL API] No active call found for conversation {}", id);
            return ResponseEntity.notFound().build();
        }
        
        CallDto dto = toDto(call);
        log.info("[CALL API] Active call found - ID: {}, Status: {}, RoomId: {}, ConversationId: {}, InitiatedAt: {}, StartedAt: {}", 
            call.getId(), call.getStatus(), call.getJitsiRoomId(), call.getConversation().getId(), 
            call.getInitiatedAt(), call.getStartedAt());
        
        return ResponseEntity.ok(dto);
    }
    
    /**
     * Lấy lịch sử cuộc gọi của conversation
     */
    @Operation(summary = "Lịch sử cuộc gọi", description = "Lấy danh sách cuộc gọi của conversation")
    @GetMapping("/conversations/{id}/calls")
    public ResponseEntity<List<CallDto>> getCallHistory(@PathVariable Long id) {
        log.info("[CALL API] GET /api/conversations/{}/calls", id);
        
        List<Call> calls = callService.getCallHistory(id);
        List<CallDto> dtos = calls.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        log.info("[CALL API] Call history retrieved - ConversationId: {}, Total calls: {}", id, calls.size());
        calls.forEach(call -> 
            log.info("[CALL API]   - Call ID: {}, Status: {}, RoomId: {}, InitiatedAt: {}", 
                call.getId(), call.getStatus(), call.getJitsiRoomId(), call.getInitiatedAt())
        );
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Cập nhật trạng thái call
     */
    @Operation(summary = "Cập nhật trạng thái call", description = "Cập nhật trạng thái cuộc gọi (RINGING, ACTIVE, ENDED, REJECTED)")
    @PatchMapping("/calls/{callId}/status")
    public ResponseEntity<CallDto> updateCallStatus(
            @PathVariable Long callId,
            @RequestParam String status) {
        log.info("[CALL API] ===== UPDATE CALL STATUS =====");
        log.info("[CALL API] PATCH /api/calls/{}/status?status={}", callId, status);
        
        try {
            Call.CallStatus newStatus = Call.CallStatus.valueOf(status.toUpperCase());
            
            // Lấy call hiện tại để log status cũ
            Call currentCall = callService.findById(callId);
            log.info("[CALL API] Current call status - ID: {}, CurrentStatus: {}, RoomId: {}", 
                currentCall.getId(), currentCall.getStatus(), currentCall.getJitsiRoomId());
            
            Call call = callService.updateCallStatus(callId, newStatus);
            CallDto dto = toDto(call);
            
            log.info("[CALL API] Status updated - ID: {}, OldStatus: {}, NewStatus: {}, RoomId: {}", 
                call.getId(), currentCall.getStatus(), call.getStatus(), call.getJitsiRoomId());
        log.info("[CALL API] ===== UPDATE CALL STATUS COMPLETE =====");
            
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.error("[CALL API] Invalid call status: {}", status);
            throw new IllegalArgumentException("Invalid call status: " + status + 
                ". Valid values: INITIATED, RINGING, ACTIVE, ENDED, REJECTED");
        }
    }
    
    /**
     * Reject call
     */
    @Operation(summary = "Từ chối cuộc gọi", description = "Customer từ chối cuộc gọi")
    @PostMapping("/calls/{callId}/reject")
    public ResponseEntity<CallDto> rejectCall(@PathVariable Long callId) {
        log.info("[CALL API] ===== REJECT CALL =====");
        log.info("[CALL API] POST /api/calls/{}/reject", callId);
        
        // Lấy call hiện tại để log status cũ
        Call currentCall = callService.findById(callId);
        log.info("[CALL API] Current call status - ID: {}, CurrentStatus: {}, RoomId: {}", 
            currentCall.getId(), currentCall.getStatus(), currentCall.getJitsiRoomId());
        
        Call call = callService.updateCallStatus(callId, Call.CallStatus.REJECTED);
        CallDto dto = toDto(call);
        
        log.info("[CALL API] Call rejected - ID: {}, OldStatus: {}, NewStatus: REJECTED, RoomId: {}", 
            call.getId(), currentCall.getStatus(), call.getJitsiRoomId());
        log.info("[CALL API] ===== REJECT CALL COMPLETE =====");
        
        return ResponseEntity.ok(dto);
    }
    
    private CallDto toDto(Call call) {
        CallDto dto = new CallDto();
        dto.setId(call.getId());
        dto.setConversationId(call.getConversation().getId());
        dto.setJitsiRoomId(call.getJitsiRoomId());
        dto.setJitsiRoomUrl(call.getJitsiRoomUrl());
        dto.setStatus(call.getStatus().name());
        dto.setInitiatedAt(call.getInitiatedAt());
        dto.setStartedAt(call.getStartedAt());
        dto.setEndedAt(call.getEndedAt());
        dto.setInitiatedBy(call.getInitiatedBy());
        return dto;
    }
}


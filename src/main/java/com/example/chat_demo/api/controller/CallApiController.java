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
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        log.info("[API] POST /api/conversations/{}/calls/initiate", id);
        
        // Tạo call
        Call call = callService.initiateCall(id);
        
        // Lấy conversation và user
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
        User user = conversation.getUser();
        
        // Gửi message qua Telegram với inline button
        String recipientId = user.getPlatformUserId();
        String messageText = "📞 Staff đang gọi bạn!\n\nBấm nút bên dưới để tham gia cuộc gọi:";
        
        PlatformConnector connector = connectorFactory.getConnector(user.getChannelType());
        if (connector instanceof TelegramConnector) {
            TelegramConnector telegramConnector = (TelegramConnector) connector;
            telegramConnector.sendMessageWithButton(
                recipientId,
                messageText,
                "📞 Join Call",
                call.getJitsiRoomUrl()
            );
            log.info("Sent call notification to Telegram user {} with room URL {}", recipientId, call.getJitsiRoomUrl());
        } else {
            // Platform khác: gửi text message với link
            connector.sendMessage(recipientId, messageText + "\n\n" + call.getJitsiRoomUrl());
            log.info("Sent call notification to {} user {} with room URL {}", 
                user.getChannelType(), recipientId, call.getJitsiRoomUrl());
        }
        
        // Build response
        InitiateCallResponse response = new InitiateCallResponse();
        response.setCallId(call.getId());
        response.setJitsiRoomUrl(call.getJitsiRoomUrl());
        response.setJitsiRoomId(call.getJitsiRoomId());
        response.setMessage("Call initiated and notification sent to customer");
        
        log.info("Call {} initiated for conversation {}", call.getId(), id);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy thông tin call
     */
    @Operation(summary = "Lấy thông tin call", description = "Lấy thông tin cuộc gọi theo ID")
    @GetMapping("/calls/{callId}")
    public ResponseEntity<CallDto> getCall(@PathVariable Long callId) {
        Call call = callService.findById(callId);
        return ResponseEntity.ok(toDto(call));
    }
    
    /**
     * Lấy active call của conversation
     */
    @Operation(summary = "Lấy active call", description = "Lấy cuộc gọi đang active của conversation")
    @GetMapping("/conversations/{id}/calls/active")
    public ResponseEntity<CallDto> getActiveCall(@PathVariable Long id) {
        Call call = callService.getActiveCall(id);
        if (call == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(call));
    }
    
    /**
     * Lấy lịch sử cuộc gọi của conversation
     */
    @Operation(summary = "Lịch sử cuộc gọi", description = "Lấy danh sách cuộc gọi của conversation")
    @GetMapping("/conversations/{id}/calls")
    public ResponseEntity<List<CallDto>> getCallHistory(@PathVariable Long id) {
        List<Call> calls = callService.getCallHistory(id);
        List<CallDto> dtos = calls.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
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


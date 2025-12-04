package com.example.chat_demo.core.service;

import com.example.chat_demo.core.model.Call;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.repository.CallRepository;
import com.example.chat_demo.core.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CallService - Service quản lý cuộc gọi (call) với Jitsi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {
    
    private final CallRepository callRepository;
    private final ConversationRepository conversationRepository;
    private final JitsiService jitsiService;
    
    /**
     * Tạo cuộc gọi mới từ Staff
     * 
     * @param conversationId Conversation ID
     * @return Call entity đã lưu
     * @throws RuntimeException nếu conversation không tồn tại
     */
    @Transactional
    public Call initiateCall(Long conversationId) {
        log.info("[CALL SERVICE] Initiating call for conversation {}", conversationId);
        
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        
        // Kiểm tra xem có call đang active không
        List<Call.CallStatus> activeStatuses = List.of(
            Call.CallStatus.INITIATED, 
            Call.CallStatus.RINGING, 
            Call.CallStatus.ACTIVE
        );
        callRepository.findFirstByConversationAndStatusInOrderByInitiatedAtDesc(conversation, activeStatuses)
            .ifPresent(existingCall -> {
                log.warn("[CALL SERVICE] Conversation {} already has active call {} (status: {}). Ending previous call.",
                    conversationId, existingCall.getId(), existingCall.getStatus());
                // End call cũ
                existingCall.setStatus(Call.CallStatus.ENDED);
                existingCall.setEndedAt(LocalDateTime.now());
                callRepository.save(existingCall);
                log.info("[CALL SERVICE] Previous call {} ended (status changed to ENDED)", existingCall.getId());
            });
        
        // Tạo Jitsi room
        String roomId = jitsiService.generateRoomId(conversationId);
        String roomUrl = jitsiService.buildRoomUrl(roomId);
        log.info("[CALL SERVICE] Generated Jitsi room - RoomId: {}, RoomUrl: {}", roomId, roomUrl);
        
        // Lưu call vào DB
        Call call = new Call();
        call.setConversation(conversation);
        call.setJitsiRoomId(roomId);
        call.setJitsiRoomUrl(roomUrl);
        call.setStatus(Call.CallStatus.INITIATED);
        call.setInitiatedBy("staff");
        
        call = callRepository.save(call);
        log.info("[CALL SERVICE] Call saved - ID: {}, Status: INITIATED, RoomId: {}, ConversationId: {}, InitiatedAt: {}", 
            call.getId(), roomId, conversationId, call.getInitiatedAt());
        
        return call;
    }
    
    /**
     * Cập nhật trạng thái call
     * 
     * @param callId Call ID
     * @param newStatus Status mới
     * @return Call entity đã cập nhật
     * @throws RuntimeException nếu call không tồn tại
     */
    @Transactional
    public Call updateCallStatus(Long callId, Call.CallStatus newStatus) {
        log.info("[CALL SERVICE] ===== UPDATE CALL STATUS =====");
        log.info("[CALL SERVICE] Updating call {} status to {}", callId, newStatus);
        
        Call call = callRepository.findById(callId)
            .orElseThrow(() -> {
                log.error("[CALL SERVICE] Call not found: {}", callId);
                return new RuntimeException("Call not found: " + callId);
            });
        
        Call.CallStatus oldStatus = call.getStatus();
        log.info("[CALL SERVICE] Current call state - ID: {}, OldStatus: {}, RoomId: {}, ConversationId: {}, InitiatedAt: {}, StartedAt: {}, EndedAt: {}", 
            call.getId(), oldStatus, call.getJitsiRoomId(), call.getConversation().getId(), 
            call.getInitiatedAt(), call.getStartedAt(), call.getEndedAt());
        
        call.setStatus(newStatus);
        
        if (newStatus == Call.CallStatus.ACTIVE && call.getStartedAt() == null) {
            call.setStartedAt(LocalDateTime.now());
            log.info("[CALL SERVICE] Call {} started at {}", callId, call.getStartedAt());
        } else if (newStatus == Call.CallStatus.ENDED || newStatus == Call.CallStatus.REJECTED) {
            call.setEndedAt(LocalDateTime.now());
            log.info("[CALL SERVICE] Call {} ended at {}", callId, call.getEndedAt());
        }
        
        call = callRepository.save(call);
        log.info("[CALL SERVICE] Status updated - ID: {}, OldStatus: {}, NewStatus: {}, RoomId: {}", 
            call.getId(), oldStatus, newStatus, call.getJitsiRoomId());
        log.info("[CALL SERVICE] Updated call state - ID: {}, Status: {}, StartedAt: {}, EndedAt: {}", 
            call.getId(), call.getStatus(), call.getStartedAt(), call.getEndedAt());
        log.info("[CALL SERVICE] ===== UPDATE CALL STATUS COMPLETE =====");
        
        return call;
    }
    
    /**
     * Lấy call theo room ID (case-insensitive)
     * 
     * @param roomId Jitsi room ID (có thể có uppercase/lowercase)
     * @return Call entity
     * @throws RuntimeException nếu không tìm thấy
     */
    public Call findByRoomId(String roomId) {
        log.info("[CALL SERVICE] Finding call by room ID: {}", roomId);
        
        // Thử tìm exact match trước
        Optional<Call> call = callRepository.findByJitsiRoomId(roomId);
        
        // Nếu không tìm thấy, thử case-insensitive
        if (call.isEmpty()) {
            log.debug("[CALL SERVICE] Exact match not found, trying case-insensitive search");
            call = callRepository.findByJitsiRoomIdIgnoreCase(roomId);
        }
        
        if (call.isPresent()) {
            log.info("[CALL SERVICE] Call found - ID: {}, RoomId: {}, Status: {}", 
                call.get().getId(), call.get().getJitsiRoomId(), call.get().getStatus());
            return call.get();
        }
        
        log.error("[CALL SERVICE] Call not found for room ID: {}", roomId);
        throw new RuntimeException("Call not found for room: " + roomId);
    }
    
    /**
     * Lấy call theo ID
     * 
     * @param callId Call ID
     * @return Call entity
     * @throws RuntimeException nếu không tìm thấy
     */
    public Call findById(Long callId) {
        log.debug("Finding call by ID: {}", callId);
        return callRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Call not found: " + callId));
    }
    
    /**
     * Lấy lịch sử cuộc gọi của conversation
     * 
     * @param conversationId Conversation ID
     * @return Danh sách call, sắp xếp từ mới đến cũ
     */
    public List<Call> getCallHistory(Long conversationId) {
        log.debug("Getting call history for conversation {}", conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        return callRepository.findByConversationOrderByInitiatedAtDesc(conversation);
    }
    
    /**
     * Lấy active call của conversation (nếu có)
     * 
     * @param conversationId Conversation ID
     * @return Call entity nếu có active call, null nếu không
     */
    public Call getActiveCall(Long conversationId) {
        log.info("[CALL SERVICE] Getting active call for conversation {}", conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        
        List<Call.CallStatus> activeStatuses = List.of(
            Call.CallStatus.INITIATED, 
            Call.CallStatus.RINGING, 
            Call.CallStatus.ACTIVE
        );
        
        Call activeCall = callRepository.findFirstByConversationAndStatusInOrderByInitiatedAtDesc(conversation, activeStatuses)
            .orElse(null);
        
        if (activeCall != null) {
            log.info("[CALL SERVICE] Active call found - ID: {}, Status: {}, RoomId: {}, ConversationId: {}, InitiatedAt: {}", 
                activeCall.getId(), activeCall.getStatus(), activeCall.getJitsiRoomId(), 
                conversationId, activeCall.getInitiatedAt());
        } else {
            log.info("[CALL SERVICE] No active call found for conversation {}", conversationId);
        }
        
        return activeCall;
    }
}


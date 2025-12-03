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
        log.info("Initiating call for conversation {}", conversationId);
        
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
                log.warn("Conversation {} already has active call {} (status: {}). Ending previous call.",
                    conversationId, existingCall.getId(), existingCall.getStatus());
                // End call cũ
                existingCall.setStatus(Call.CallStatus.ENDED);
                existingCall.setEndedAt(LocalDateTime.now());
                callRepository.save(existingCall);
            });
        
        // Tạo Jitsi room
        String roomId = jitsiService.generateRoomId(conversationId);
        String roomUrl = jitsiService.buildRoomUrl(roomId);
        
        // Lưu call vào DB
        Call call = new Call();
        call.setConversation(conversation);
        call.setJitsiRoomId(roomId);
        call.setJitsiRoomUrl(roomUrl);
        call.setStatus(Call.CallStatus.INITIATED);
        call.setInitiatedBy("staff");
        
        call = callRepository.save(call);
        log.info("Initiated call {} for conversation {} (room: {})", call.getId(), conversationId, roomId);
        
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
        log.info("Updating call {} status to {}", callId, newStatus);
        
        Call call = callRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Call not found: " + callId));
        
        Call.CallStatus oldStatus = call.getStatus();
        call.setStatus(newStatus);
        
        if (newStatus == Call.CallStatus.ACTIVE && call.getStartedAt() == null) {
            call.setStartedAt(LocalDateTime.now());
            log.info("Call {} started at {}", callId, call.getStartedAt());
        } else if (newStatus == Call.CallStatus.ENDED || newStatus == Call.CallStatus.REJECTED) {
            call.setEndedAt(LocalDateTime.now());
            log.info("Call {} ended at {}", callId, call.getEndedAt());
        }
        
        call = callRepository.save(call);
        log.info("Updated call {} status from {} to {}", callId, oldStatus, newStatus);
        
        return call;
    }
    
    /**
     * Lấy call theo room ID
     * 
     * @param roomId Jitsi room ID
     * @return Call entity
     * @throws RuntimeException nếu không tìm thấy
     */
    public Call findByRoomId(String roomId) {
        log.debug("Finding call by room ID: {}", roomId);
        return callRepository.findByJitsiRoomId(roomId)
            .orElseThrow(() -> new RuntimeException("Call not found for room: " + roomId));
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
        log.debug("Getting active call for conversation {}", conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        
        List<Call.CallStatus> activeStatuses = List.of(
            Call.CallStatus.INITIATED, 
            Call.CallStatus.RINGING, 
            Call.CallStatus.ACTIVE
        );
        
        return callRepository.findFirstByConversationAndStatusInOrderByInitiatedAtDesc(conversation, activeStatuses)
            .orElse(null);
    }
}


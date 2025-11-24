package com.example.chat_demo.api.controller;

import com.example.chat_demo.api.dto.*;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import com.example.chat_demo.core.repository.MessageRepository;
import com.example.chat_demo.omnichannel.bus.OmnichannelMessageBus;
import com.example.chat_demo.omnichannel.connector.ConnectorFactory;
import com.example.chat_demo.omnichannel.connector.PlatformConnector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatApiController - API cho Staff Dashboard
 */
@Slf4j
@RestController
@Tag(name = "Staff Chat API", description = "Các API phục vụ Staff Dashboard để xem và gửi tin nhắn.")
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class ChatApiController {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OmnichannelMessageBus messageBus;
    private final ConnectorFactory connectorFactory;
    
    /**
     * Lấy danh sách tất cả conversations
     */
    @Operation(summary = "Danh sách conversations", description = "Trả về danh sách toàn bộ hội thoại, sắp xếp theo thời gian tin nhắn gần nhất.")
    @ApiResponse(responseCode = "200", description = "Danh sách conversations",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConversationDto.class)))
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getConversations() {
        log.info("[API] GET /api/conversations requested");
        List<Conversation> conversations = conversationRepository.findAll(
            Sort.by(Sort.Direction.DESC, "lastMessageAt")
        );
        log.info("Fetched {} conversations", conversations.size());
        
        List<ConversationDto> dtos = conversations.stream()
            .map(this::toConversationDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Lấy thông tin conversation kèm 50 tin nhắn gần nhất
     * - Mặc định: load 50 tin nhắn gần nhất
     * - Scroll lên: dùng before={message_id} để load tin cũ hơn
     */
    @Operation(summary = "Chi tiết conversation với lịch sử chat", 
              description = "Lấy thông tin conversation và 50 tin nhắn gần nhất. Dùng before={message_id} để load tin cũ hơn khi scroll lên.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thông tin conversation và messages",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConversationDetailDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hội thoại")
    })
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDetailDto> getConversation(
            @PathVariable Long id,
            @RequestParam(required = false) Long before,  // Load tin cũ hơn message_id này (scroll lên)
            @RequestParam(defaultValue = "50") int limit) {
        log.info("[API] GET /api/conversations/{}?before={}&limit={} requested", id, before, limit);
        
        // Tìm conversation
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        // Load messages và build response
        ConversationDetailDto response = buildConversationDetailDto(conversation, before, limit);
        
        log.info("Fetched conversation {} with {} messages (hasMore={}, totalCount={})", 
            conversation.getId(), response.getMessages().size(), response.isHasMore(), response.getTotalCount());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gửi message từ Staff
     */
    @Operation(summary = "Gửi tin nhắn từ staff", description = "Gửi tin nhắn từ nhân viên đến người dùng cuối qua platform tương ứng.")
    @ApiResponse(responseCode = "200", description = "Tin nhắn đã gửi",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageDto.class)))
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long id,
            @RequestBody SendMessageRequest request) {
        log.info("[API] POST /api/conversations/{}/messages payload={}", id, request);
        
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        User user = conversation.getUser();
        
        // Lấy connector tương ứng
        PlatformConnector connector = connectorFactory.getConnector(user.getChannelType());
        
        // Gửi message qua platform
        try {
            connector.sendMessage(user.getPlatformUserId(), request.getContent());
        } catch (Exception e) {
            log.error("Failed to send message via connector", e);
            throw new RuntimeException("Failed to send message", e);
        }
        
        // Lưu message vào DB
        Message message = messageBus.saveOutboundMessage(
            request.getContent(), 
            user, 
            conversation
        );
        
        // Update message status
        message.setStatus(Message.MessageStatus.DELIVERED);
        message.setSentAt(LocalDateTime.now());
        messageRepository.save(message);
        log.info("Sent outbound message {} to user {}", message.getId(), user.getId());
        log.info("[API] POST /api/conversations/{}/messages response messageId={}", id, message.getId());
        
        return ResponseEntity.ok(toMessageDto(message));
    }
    
    // Helper methods
    
    /**
     * Load messages với infinite scroll logic
     */
    private MessageListDto loadMessages(Conversation conversation, Long before, int limit) {
        List<Message> messages;
        boolean hasMore;
        Pageable pageable = PageRequest.of(0, limit + 1); // Load thêm 1 để check hasMore
        
        if (before != null) {
            // Scroll lên: load tin cũ hơn message_id
            messages = messageRepository.findOlderMessages(conversation, before, pageable);
            hasMore = messages.size() > limit;
            if (hasMore) {
                messages = messages.subList(0, limit);
            }
        } else {
            // Lần đầu: load tin mới nhất
            messages = messageRepository.findLatestMessages(conversation, pageable);
            hasMore = messages.size() > limit;
            if (hasMore) {
                messages = messages.subList(0, limit);
            }
            // Reverse để hiển thị từ cũ đến mới (giống chat app)
            Collections.reverse(messages);
        }
        
        // Convert to DTO
        List<MessageDto> messageDtos = messages.stream()
            .map(this::toMessageDto)
            .collect(Collectors.toList());
        
        // Build response
        MessageListDto response = new MessageListDto();
        response.setMessages(messageDtos);
        response.setHasMore(hasMore);
        
        if (!messages.isEmpty()) {
            response.setOldestMessageId(messages.get(0).getId());
            response.setNewestMessageId(messages.get(messages.size() - 1).getId());
        }
        
        // Đếm tổng số tin nhắn
        long totalCount = messageRepository.countByConversation(conversation);
        response.setTotalCount((int) totalCount);
        
        return response;
    }
    
    /**
     * Build ConversationDetailDto từ conversation và messages
     */
    private ConversationDetailDto buildConversationDetailDto(Conversation conversation, Long before, int limit) {
        ConversationDetailDto response = new ConversationDetailDto();
        
        // Conversation info
        populateConversationInfo(response, conversation);
        
        // Load messages
        MessageListDto messageList = loadMessages(conversation, before, limit);
        response.setMessages(messageList.getMessages());
        response.setHasMore(messageList.isHasMore());
        response.setOldestMessageId(messageList.getOldestMessageId());
        response.setNewestMessageId(messageList.getNewestMessageId());
        response.setTotalCount(messageList.getTotalCount());
        
        return response;
    }
    
    /**
     * Populate conversation info vào DTO
     */
    private void populateConversationInfo(ConversationDetailDto dto, Conversation conv) {
        dto.setId(conv.getId());
        dto.setUserId(conv.getUser().getId());
        dto.setUserName(formatUserName(conv.getUser()));
        dto.setUserPlatformId(conv.getUser().getPlatformUserId());
        dto.setChannelType(conv.getUser().getChannelType().name());
        dto.setStatus(conv.getStatus());
        dto.setStartedAt(conv.getStartedAt());
        dto.setLastMessageAt(conv.getLastMessageAt());
    }
    
    /**
     * Format user name từ firstName và lastName
     */
    private String formatUserName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
    
    private ConversationDto toConversationDto(Conversation conv) {
        ConversationDto dto = new ConversationDto();
        dto.setId(conv.getId());
        dto.setUserId(conv.getUser().getId());
        dto.setUserName(formatUserName(conv.getUser()));
        dto.setUserPlatformId(conv.getUser().getPlatformUserId());
        dto.setChannelType(conv.getUser().getChannelType().name());
        dto.setStatus(conv.getStatus());
        dto.setStartedAt(conv.getStartedAt());
        dto.setLastMessageAt(conv.getLastMessageAt());
        return dto;
    }
    
    private MessageDto toMessageDto(Message msg) {
        MessageDto dto = new MessageDto();
        dto.setId(msg.getId());
        dto.setContent(msg.getContent());
        dto.setMessageType(msg.getMessageType());
        
        // Xử lý null cho direction và status
        if (msg.getDirection() != null) {
            dto.setDirection(msg.getDirection().name());
        }
        if (msg.getStatus() != null) {
            dto.setStatus(msg.getStatus().name());
        }
        
        dto.setReceivedAt(msg.getReceivedAt());
        dto.setSentAt(msg.getSentAt());
        
        // Xử lý null cho user
        if (msg.getUser() != null) {
            dto.setUserId(msg.getUser().getId());
            dto.setUserName(
                msg.getUser().getFirstName() != null ? 
                    msg.getUser().getFirstName() : 
                    "Unknown"
            );
        }
        
        return dto;
    }
}


package com.example.chat_demo.api.controller;

import com.example.chat_demo.api.dto.*;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import com.example.chat_demo.core.repository.MessageRepository;
import com.example.chat_demo.core.repository.UserRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final UserRepository userRepository;
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
     * Lấy thông tin 1 conversation
     */
    @Operation(summary = "Chi tiết conversation", description = "Lấy thông tin chi tiết của 1 hội thoại theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thông tin hội thoại",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConversationDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hội thoại")
    })
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable Long id) {
        log.info("[API] GET /api/conversations/{} requested", id);
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));
        log.info("Fetched conversation {} for user {}", conversation.getId(), conversation.getUser().getId());
        
        return ResponseEntity.ok(toConversationDto(conversation));
    }
    
    /**
     * Lấy messages của 1 conversation (có phân trang)
     */
    @Operation(summary = "Danh sách tin nhắn", description = "Lấy danh sách tin nhắn của một hội thoại (có phân trang).")
    @ApiResponse(responseCode = "200", description = "Danh sách tin nhắn",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessagePageDto.class)))
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<MessagePageDto> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("[API] GET /api/conversations/{}/messages?page={}&size={} requested", id, page, size);
        
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        Pageable pageable = PageRequest.of(page, size, 
            Sort.by(Sort.Direction.ASC, "receivedAt"));
        
        Page<Message> messagePage = messageRepository.findByConversation(conversation, pageable);
        log.info("Fetched {} messages (page {}/{}) for conversation {}", messagePage.getNumberOfElements(), page, messagePage.getTotalPages(), conversation.getId());
        
        List<MessageDto> messages = messagePage.getContent().stream()
            .map(this::toMessageDto)
            .collect(Collectors.toList());
        
        MessagePageDto pageDto = new MessagePageDto();
        pageDto.setMessages(messages);
        pageDto.setTotalPages(messagePage.getTotalPages());
        pageDto.setTotalElements(messagePage.getTotalElements());
        pageDto.setCurrentPage(page);
        
        return ResponseEntity.ok(pageDto);
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
    private ConversationDto toConversationDto(Conversation conv) {
        ConversationDto dto = new ConversationDto();
        dto.setId(conv.getId());
        dto.setUserId(conv.getUser().getId());
        dto.setUserName(conv.getUser().getFirstName() + " " + 
            (conv.getUser().getLastName() != null ? conv.getUser().getLastName() : ""));
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
        dto.setDirection(msg.getDirection().name());
        dto.setStatus(msg.getStatus().name());
        dto.setReceivedAt(msg.getReceivedAt());
        dto.setSentAt(msg.getSentAt());
        dto.setUserId(msg.getUser().getId());
        dto.setUserName(msg.getUser().getFirstName());
        return dto;
    }
}


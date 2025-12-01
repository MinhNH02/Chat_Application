package com.example.chat_demo.api.controller;

import com.example.chat_demo.api.dto.*;
import com.example.chat_demo.api.mapper.MessageMapper;
import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.ConversationRepository;
import com.example.chat_demo.core.repository.MessageRepository;
import com.example.chat_demo.omnichannel.bus.OmnichannelMessageBus;
import com.example.chat_demo.omnichannel.connector.ConnectorFactory;
import com.example.chat_demo.omnichannel.connector.PlatformConnector;
import com.example.chat_demo.omnichannel.realtime.RealtimeMessagePublisher;
import com.example.chat_demo.omnichannel.connector.TelegramConnector;
import com.example.chat_demo.storage.MediaStorageService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ChatApiController - API cho Staff Dashboard
 */
@Slf4j
@RestController
@Tag(name = "Staff Chat API", description = "Các API phục vụ Staff Dashboard để xem và gửi tin nhắn.")
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@RequiredArgsConstructor
public class ChatApiController {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OmnichannelMessageBus messageBus;
    private final ConnectorFactory connectorFactory;
    private final MessageMapper messageMapper;
    private final RealtimeMessagePublisher realtimeMessagePublisher;
    private final MediaStorageService mediaStorageService;
    
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
        
        // Xác định recipient ID: Discord dùng channel ID, các platform khác dùng user ID
        String recipientId;
        if (user.getChannelType() == com.example.chat_demo.common.ChannelType.DISCORD) {
            // Discord: dùng channel ID từ conversation
            recipientId = conversation.getChannelId();
            if (recipientId == null || recipientId.isBlank()) {
                throw new RuntimeException("Discord conversation missing channel ID");
            }
        } else {
            // Telegram, Messenger, etc.: dùng user ID
            recipientId = user.getPlatformUserId();
        }
        
        // Lưu message vào DB với status PENDING (trước khi gửi)
        Message message = messageBus.saveOutboundMessage(
            request.getContent(), 
            user, 
            conversation
        );
        
        // Gửi message qua platform và cập nhật status
        try {
            connector.sendMessage(recipientId, request.getContent());
            
            // Thành công: update status = DELIVERED
            message.setStatus(Message.MessageStatus.DELIVERED);
            message.setSentAt(LocalDateTime.now());
            messageRepository.save(message);
            
            // Publish lại để frontend nhận update
            realtimeMessagePublisher.publish(message);
            
            log.info("Sent outbound message {} to user {} (status: DELIVERED)", message.getId(), user.getId());
            log.info("[API] POST /api/conversations/{}/messages response messageId={}", id, message.getId());
            
            return ResponseEntity.ok(messageMapper.toDto(message));
            
        } catch (Exception e) {
            log.error("Failed to send message via connector for message {}", message.getId(), e);
            
            // Thất bại: update status = FAILED
            message.setStatus(Message.MessageStatus.FAILED);
            messageRepository.save(message);
            
            // Publish lại để frontend nhận update (với status FAILED)
            realtimeMessagePublisher.publish(message);
            
            log.warn("Message {} failed to send, status set to FAILED", message.getId());
            
            // Trả về message với status FAILED (frontend sẽ hiển thị icon ✗)
            return ResponseEntity.ok(messageMapper.toDto(message));
        }
    }
    
    /**
     * Gửi file từ Staff
     */
    @Operation(summary = "Gửi file từ staff", description = "Upload file và gửi đến người dùng cuối qua platform tương ứng.")
    @ApiResponse(responseCode = "200", description = "File đã gửi",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageDto.class)))
    @PostMapping(
            value = "/conversations/{id}/messages/file",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public ResponseEntity<MessageDto> sendFile(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "content", required = false) String content) {
        log.info("[API] POST /api/conversations/{}/messages/file filename={}, size={}", 
                id, file.getOriginalFilename(), file.getSize());
        
        Conversation conversation = conversationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        User user = conversation.getUser();
        
        // Lưu message vào DB với status PENDING (trước khi upload và gửi)
        String messageContent;
        if (content != null) {
            String trimmed = content.trim();
            if (!trimmed.isEmpty() && !"string".equalsIgnoreCase(trimmed)) {
                messageContent = trimmed;
            } else {
                messageContent = String.format("[File: %s]", file.getOriginalFilename());
            }
        } else {
            messageContent = String.format("[File: %s]", file.getOriginalFilename());
        }
        
        Message message = messageBus.saveOutboundMessage(
            messageContent, 
            user, 
            conversation
        );
        
        try {
            // Upload file lên MinIO
            String objectKey = mediaStorageService.uploadFile(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                conversation.getId(),
                message.getId()
            );
            
            // Cập nhật message với attachment info
            message.setAttachmentUrl(objectKey);
            message.setAttachmentType(getAttachmentTypeFromContentType(file.getContentType()));
            message.setAttachmentFilename(file.getOriginalFilename());
            message.setAttachmentSize(file.getSize());
            message.setMessageType(getAttachmentTypeFromContentType(file.getContentType()));
            messageRepository.save(message);
            
            // Gửi file qua platform
            PlatformConnector connector = connectorFactory.getConnector(user.getChannelType());
            String recipientId;
            if (user.getChannelType() == com.example.chat_demo.common.ChannelType.DISCORD) {
                recipientId = conversation.getChannelId();
                if (recipientId == null || recipientId.isBlank()) {
                    throw new RuntimeException("Discord conversation missing channel ID");
                }
            } else {
                recipientId = user.getPlatformUserId();
            }

            // Nếu là Telegram: dùng API media với bytes trực tiếp (không dựa vào URL nội bộ 127.0.0.1)
            if (connector instanceof TelegramConnector) {
                TelegramConnector telegramConnector = (TelegramConnector) connector;
                byte[] bytes = file.getBytes();
                String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
                String filename = file.getOriginalFilename();

                String type = message.getAttachmentType();
                String lowerName = filename != null ? filename.toLowerCase() : "";

                if ("image".equals(type) && !lowerName.endsWith(".gif")) {
                    telegramConnector.sendPhotoBytes(recipientId, bytes, filename, mimeType, messageContent);
                } else if ("image".equals(type) && lowerName.endsWith(".gif")) {
                    telegramConnector.sendAnimationBytes(recipientId, bytes, filename, mimeType, messageContent);
                } else if ("video".equals(type)) {
                    telegramConnector.sendVideoBytes(recipientId, bytes, filename, mimeType, messageContent);
                } else {
                    telegramConnector.sendDocumentBytes(recipientId, bytes, filename, mimeType, messageContent);
                }
            } else {
                // Các kênh khác: fallback gửi text
                connector.sendMessage(recipientId, messageContent);
            }
            
            // Thành công: update status = DELIVERED
            message.setStatus(Message.MessageStatus.DELIVERED);
            message.setSentAt(LocalDateTime.now());
            messageRepository.save(message);
            
            // Publish lại để frontend nhận update
            realtimeMessagePublisher.publish(message);
            
            log.info("Sent outbound file message {} to user {} (status: DELIVERED)", message.getId(), user.getId());
            return ResponseEntity.ok(messageMapper.toDto(message));
            
        } catch (Exception e) {
            log.error("Failed to send file via connector for message {}", message.getId(), e);
            
            // Thất bại: update status = FAILED
            message.setStatus(Message.MessageStatus.FAILED);
            messageRepository.save(message);
            
            // Publish lại để frontend nhận update
            realtimeMessagePublisher.publish(message);
            
            return ResponseEntity.status(500).body(messageMapper.toDto(message));
        }
    }
    
    /**
     * Lấy pre-signed URL để download/view file
     */
    @Operation(summary = "Lấy URL để download file", description = "Tạo pre-signed URL để frontend có thể download/view file từ MinIO.")
    @GetMapping("/messages/{messageId}/file-url")
    public ResponseEntity<Map<String, String>> getFileUrl(@PathVariable Long messageId) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Message not found"));
        
        if (message.getAttachmentUrl() == null || message.getAttachmentUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message has no attachment"));
        }
        
        // Tạo pre-signed URL (expiry: 1 hour)
        String presignedUrl = mediaStorageService.getPresignedUrl(message.getAttachmentUrl(), 3600);
        
        return ResponseEntity.ok(Map.of(
            "url", presignedUrl,
            "filename", message.getAttachmentFilename() != null ? message.getAttachmentFilename() : "file",
            "type", message.getAttachmentType() != null ? message.getAttachmentType() : "document"
        ));
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
            .map(messageMapper::toDto)
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
    
    /**
     * Map content type sang attachment type
     */
    private String getAttachmentTypeFromContentType(String contentType) {
        if (contentType == null) {
            return "document";
        }
        if (contentType.startsWith("image/")) {
            return "image";
        } else if (contentType.startsWith("video/")) {
            return "video";
        } else if (contentType.startsWith("audio/")) {
            return "audio";
        } else {
            return "document";
        }
    }
    
}


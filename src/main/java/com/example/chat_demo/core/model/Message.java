package com.example.chat_demo.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;
    
    @Column(name = "platform_message_id")
    private String platformMessageId;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "message_type")
    private String messageType = "text";
    
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Enumerated(EnumType.STRING)
    private MessageDirection direction;
    
    @Enumerated(EnumType.STRING)
    private MessageStatus status;
    
    public enum MessageDirection {
        INBOUND,    // Từ user đến system
        OUTBOUND    // Từ system đến user
    }
    
    public enum MessageStatus {
        PENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }
}


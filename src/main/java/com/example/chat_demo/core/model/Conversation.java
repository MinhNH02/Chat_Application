package com.example.chat_demo.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "ticket_id")
    private String ticketId;
    
    private String status = "OPEN";  // OPEN, CLOSED, PENDING
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}


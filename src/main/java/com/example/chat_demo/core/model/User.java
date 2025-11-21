package com.example.chat_demo.core.model;

import com.example.chat_demo.common.ChannelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform_user_id", "channel_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId;  // chat_id / psid / zalo_user_id
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;
    
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    @Column(name = "first_contact_at")
    private LocalDateTime firstContactAt;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (firstContactAt == null) {
            firstContactAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


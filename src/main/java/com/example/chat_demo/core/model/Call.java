package com.example.chat_demo.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    
    @Column(name = "jitsi_room_id", unique = true, nullable = false, length = 255)
    private String jitsiRoomId;
    
    @Column(name = "jitsi_room_url", nullable = false, length = 500)
    private String jitsiRoomUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status;
    
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "initiated_by", nullable = false, length = 50)
    private String initiatedBy;  // "staff" hoặc "customer"
    
    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) {
            initiatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CallStatus.INITIATED;
        }
    }
    
    public enum CallStatus {
        INITIATED,  // Staff vừa tạo call
        RINGING,    // Đang đợi customer join
        ACTIVE,     // Cả 2 đã join, đang nói chuyện
        ENDED,      // Cuộc gọi kết thúc
        REJECTED    // Customer từ chối
    }
}


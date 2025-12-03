package com.example.chat_demo.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * CallDto - DTO để trả về thông tin call cho frontend
 */
@Data
public class CallDto {
    private Long id;
    private Long conversationId;
    private String jitsiRoomId;
    private String jitsiRoomUrl;
    private String status;  // INITIATED, RINGING, ACTIVE, ENDED, REJECTED
    private LocalDateTime initiatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String initiatedBy;  // "staff" hoặc "customer"
}


package com.example.chat_demo.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private String content;
    private String messageType;
    private String direction;
    private String status;
    private LocalDateTime receivedAt;
    private LocalDateTime sentAt;
    private Long userId;
    private String userName;
    
    // Attachment info
    private String attachmentUrl;  // MinIO object key hoặc pre-signed URL
    private String attachmentType;
    private String attachmentFilename;
    private Long attachmentSize;
}


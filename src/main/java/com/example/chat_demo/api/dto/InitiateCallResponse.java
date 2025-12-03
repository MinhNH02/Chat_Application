package com.example.chat_demo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * InitiateCallResponse - Response khi staff initiate call
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateCallResponse {
    private Long callId;
    private String jitsiRoomUrl;
    private String jitsiRoomId;
    private String message;  // Thông báo kết quả
}


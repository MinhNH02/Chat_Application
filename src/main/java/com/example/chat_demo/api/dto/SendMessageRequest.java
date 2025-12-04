package com.example.chat_demo.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank(message = "Content cannot be blank")
    private String content;
}


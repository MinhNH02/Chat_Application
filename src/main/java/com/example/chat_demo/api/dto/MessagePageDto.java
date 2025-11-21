package com.example.chat_demo.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class MessagePageDto {
    private List<MessageDto> messages;
    private int totalPages;
    private long totalElements;
    private int currentPage;
}


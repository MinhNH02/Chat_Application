package com.example.chat_demo.core.realtime;

import com.example.chat_demo.api.dto.MessageDto;
import com.example.chat_demo.api.mapper.MessageMapper;
import com.example.chat_demo.core.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * RealtimeMessagePublisher - phát message qua WebSocket/STOMP đến frontend
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageMapper messageMapper;

    public void publish(Message message) {
        if (message.getConversation() == null) {
            log.warn("Skip realtime publish because conversation is null for message {}", message.getId());
            return;
        }

        MessageDto payload = messageMapper.toDto(message);
        String destination = "/topic/conversations/" + message.getConversation().getId();
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Published message {} to destination {}", message.getId(), destination);
        } catch (Exception ex) {
            log.error("Failed to publish realtime message {}", message.getId(), ex);
        }
    }
}


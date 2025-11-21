package com.example.chat_demo.omnichannel.connector;

import com.example.chat_demo.common.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * TelegramConnector - Gửi message qua Telegram Bot API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramConnector implements PlatformConnector {
    
    @Value("${platform.telegram.api-url:https://api.telegram.org/bot}")
    private String telegramApiUrl;
    
    @Value("${platform.telegram.bot-token}")
    private String botToken;
    
    private final RestTemplate restTemplate;
    
    @Override
    public ChannelType getChannelType() {
        return ChannelType.TELEGRAM;
    }
    
    @Override
    public void sendMessage(String recipientId, String message) {
        try {
            String url = telegramApiUrl + botToken + "/sendMessage";
            log.info("Sending Telegram message to {} via {}", recipientId, url);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", recipientId);
            requestBody.put("text", message);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForObject(url, request, Map.class);
            
            log.info("Sent message to Telegram user: {}", recipientId);
            
        } catch (Exception e) {
            log.error("Error sending message to Telegram user: {}", recipientId, e);
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }
}


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
 * DiscordConnector - Gửi message tới Discord channel thông qua REST API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordConnector implements PlatformConnector {

    @Value("${platform.discord.bot-token}")
    private String botToken;

    private final RestTemplate restTemplate;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.DISCORD;
    }

    /**
     * recipientId sẽ là channelId (text channel) nơi bot cần gửi message
     */
    @Override
    public void sendMessage(String recipientId, String message) {
        try {
            String url = "https://discord.com/api/v10/channels/" + recipientId + "/messages";
            log.info("Sending Discord message to channel {} via {}", recipientId, url);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("content", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bot " + botToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject(url, request, Map.class);

            log.info("Sent message to Discord channel: {}", recipientId);
        } catch (Exception e) {
            log.error("Error sending message to Discord channel: {}", recipientId, e);
            throw new RuntimeException("Failed to send Discord message", e);
        }
    }
}




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
 * MessengerConnector - Gửi message qua Facebook Messenger Graph API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessengerConnector implements PlatformConnector {

    @Value("${platform.messenger.api-url:https://graph.facebook.com/v18.0}")
    private String messengerApiUrl;

    @Value("${platform.messenger.page-access-token}")
    private String pageAccessToken;

    private final RestTemplate restTemplate;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.MESSENGER;
    }

    @Override
    public void sendMessage(String recipientId, String message) {
        try {
            String url = messengerApiUrl + "/me/messages";
            log.info("Sending Messenger message to {} via {}", recipientId, url);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("recipient", Map.of("id", recipientId));
            requestBody.put("message", Map.of("text", message));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(pageAccessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            restTemplate.postForObject(url, request, Map.class);
            log.info("Sent message to Messenger user: {}", recipientId);

        } catch (Exception e) {
            log.error("Error sending message to Messenger user: {}", recipientId, e);
            throw new RuntimeException("Failed to send Messenger message", e);
        }
    }
}


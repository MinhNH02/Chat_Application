package com.example.chat_demo.webhook.controller;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.omnichannel.parser.OmnichannelParser;
import com.example.chat_demo.omnichannel.router.OmnichannelRouter;
import org.springframework.beans.factory.annotation.Value;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WebhookController - Nhận webhook từ các platform
 */
@Slf4j
@RestController
@Tag(name = "Webhook API", description = "Endpoint để Telegram/Messenger gọi vào.")
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {
    
    private final OmnichannelParser parser;
    private final OmnichannelRouter router;
    
    @Value("${platform.messenger.verify-token:}")
    private String messengerVerifyToken;
    
    /**
     * Webhook endpoint cho Telegram
     */
    @Operation(summary = "Telegram Webhook", description = "Endpoint để Telegram gửi sự kiện tin nhắn đến hệ thống.")
    @PostMapping("/telegram")
    public ResponseEntity<String> receiveTelegramWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("[API] POST /webhook/telegram received payload: {}", payload);
            
            // Parse message
            var unifiedMessage = parser.parse(ChannelType.TELEGRAM, payload);
            
            if (unifiedMessage == null) {
                log.warn("Failed to parse Telegram webhook");
                return ResponseEntity.ok("OK");
            }
            
            // Route message
            router.routeMessage(unifiedMessage);
            log.info("[API] POST /webhook/telegram processed message {}", unifiedMessage.getPlatformMessageId());
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("Error processing Telegram webhook", e);
            return ResponseEntity.status(500).body("Error");
        }
    }
    
    /**
     * Webhook endpoint cho Messenger
     */
    @Operation(summary = "Messenger Webhook", description = "Endpoint để Facebook Messenger gửi sự kiện tin nhắn.")
    @PostMapping("/messenger")
    public ResponseEntity<String> receiveMessengerWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("[API] POST /webhook/messenger received payload: {}", payload);
            
            var unifiedMessage = parser.parse(ChannelType.MESSENGER, payload);
            
            if (unifiedMessage == null) {
                log.warn("Failed to parse Messenger webhook");
                return ResponseEntity.ok("OK");
            }
            
            router.routeMessage(unifiedMessage);
            log.info("[API] POST /webhook/messenger processed message {}", unifiedMessage.getPlatformMessageId());
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("Error processing Messenger webhook", e);
            return ResponseEntity.status(500).body("Error");
        }
    }
    
    /**
     * Webhook verification cho Messenger (GET request)
     */
    @Operation(summary = "Messenger Webhook Verification", description = "Endpoint để Facebook xác minh webhook khi đăng ký.")
    @GetMapping("/messenger")
    public ResponseEntity<String> verifyMessengerWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        
        if ("subscribe".equals(mode) && tokenMatches(token)) {
            log.info("Messenger webhook verified");
            return ResponseEntity.ok(challenge);
        }
        
        log.warn("Messenger webhook verification failed");
        return ResponseEntity.status(403).body("Forbidden");
    }
    
    private boolean tokenMatches(String token) {
        return messengerVerifyToken != null && !messengerVerifyToken.isEmpty()
                && messengerVerifyToken.equals(token);
    }
}


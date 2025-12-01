package com.example.chat_demo.webhook.controller;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.omnichannel.parser.OmnichannelParser;
import com.example.chat_demo.omnichannel.router.OmnichannelRouter;
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
@Tag(name = "Webhook API", description = "Endpoint để Telegram gửi webhook vào.")
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {
    
    private final OmnichannelParser parser;
    private final OmnichannelRouter router;
    
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
    
}


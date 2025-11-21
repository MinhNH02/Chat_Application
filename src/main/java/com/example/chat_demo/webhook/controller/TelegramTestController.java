package com.example.chat_demo.webhook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * TelegramTestController - Helper endpoints để test Telegram
 */
@Slf4j
@RestController
@Tag(name = "Telegram Test API", description = "Các endpoint hỗ trợ kiểm tra nhanh webhook/token Telegram.")
@RequestMapping("/test/telegram")
@RequiredArgsConstructor
public class TelegramTestController {
    
    @Value("${platform.telegram.bot-token}")
    private String botToken;
    
    @Value("${platform.telegram.api-url:https://api.telegram.org/bot}")
    private String telegramApiUrl;
    
    private final RestTemplate restTemplate;
    
    /**
     * Kiểm tra thông tin webhook hiện tại
     */
    @Operation(summary = "Xem webhook info", description = "Lấy thông tin webhook hiện tại từ Telegram Bot API.")
    @GetMapping("/webhook-info")
    public ResponseEntity<Map<String, Object>> getWebhookInfo() {
        try {
            String url = telegramApiUrl + botToken + "/getWebhookInfo";
            log.info("[API] GET /test/telegram/webhook-info calling {}", url);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("[API] GET /test/telegram/webhook-info success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting webhook info", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Setup webhook (POST với URL)
     */
    @Operation(summary = "Thiết lập webhook", description = "Thiết lập webhook Telegram bằng cách truyền URL.")
    @PostMapping("/setup-webhook")
    public ResponseEntity<Map<String, Object>> setupWebhook(@RequestParam String url) {
        try {
            String apiUrl = telegramApiUrl + botToken + "/setWebhook";
            log.info("[API] POST /test/telegram/setup-webhook target {}", url);
            Map<String, String> params = new HashMap<>();
            params.put("url", url);
            
            Map<String, Object> response = restTemplate.postForObject(
                apiUrl + "?url=" + url, 
                null, 
                Map.class
            );
            log.info("[API] POST /test/telegram/setup-webhook success");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error setting up webhook", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Xóa webhook
     */
    @Operation(summary = "Xóa webhook", description = "Xóa webhook hiện tại của bot.")
    @PostMapping("/delete-webhook")
    public ResponseEntity<Map<String, Object>> deleteWebhook() {
        try {
            String url = telegramApiUrl + botToken + "/deleteWebhook";
            log.info("[API] POST /test/telegram/delete-webhook");
            Map<String, Object> response = restTemplate.postForObject(url, null, Map.class);
            log.info("[API] POST /test/telegram/delete-webhook success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting webhook", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Test gửi message (dùng chat_id)
     */
    @Operation(summary = "Gửi message test", description = "Gửi thử một tin nhắn text tới user bằng chat_id.")
    @PostMapping("/send-message")
    public ResponseEntity<Map<String, Object>> sendTestMessage(
            @RequestParam String chatId,
            @RequestParam String text) {
        try {
            String url = telegramApiUrl + botToken + "/sendMessage";
            log.info("[API] POST /test/telegram/send-message chatId={} text={}", chatId, text);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", text);
            
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            log.info("[API] POST /test/telegram/send-message success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending test message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}


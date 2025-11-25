package com.example.chat_demo.webhook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MessengerTestController - Helper endpoints để test Messenger/Facebook
 */
@Slf4j
@RestController
@Tag(name = "Messenger Test API", description = "Các endpoint hỗ trợ kiểm tra nhanh webhook/token Messenger.")
@RequestMapping("/test/messenger")
@RequiredArgsConstructor
public class MessengerTestController {
    
    @Value("${platform.messenger.page-access-token}")
    private String pageAccessToken;
    
    @Value("${platform.messenger.api-url:https://graph.facebook.com/v18.0}")
    private String messengerApiUrl;
    
    private final RestTemplate restTemplate;
    
    /**
     * Lấy thông tin Page (Page ID, name, etc.)
     */
    @Operation(summary = "Xem Page info", description = "Lấy thông tin Page từ Facebook Graph API.")
    @GetMapping("/page-info")
    public ResponseEntity<Map<String, Object>> getPageInfo() {
        try {
            String url = messengerApiUrl + "/me?fields=id,name&access_token=" + pageAccessToken;
            log.info("[API] GET /test/messenger/page-info calling {}", url);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("[API] GET /test/messenger/page-info success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting page info", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Lấy thông tin user profile từ PSID
     */
    @Operation(summary = "Lấy user profile", description = "Lấy thông tin user (firstName, lastName) từ PSID qua Graph API.")
    @GetMapping("/user-profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@RequestParam String psid) {
        try {
            String url = messengerApiUrl + "/" + psid + "?fields=first_name,last_name,profile_pic&access_token=" + pageAccessToken;
            log.info("[API] GET /test/messenger/user-profile psid={}", psid);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("[API] GET /test/messenger/user-profile success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting user profile", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Test gửi message (dùng PSID)
     */
    @Operation(summary = "Gửi message test", description = "Gửi thử một tin nhắn text tới user bằng PSID.")
    @PostMapping("/send-message")
    public ResponseEntity<Map<String, Object>> sendTestMessage(
            @RequestParam String psid,
            @RequestParam String text) {
        try {
            String url = messengerApiUrl + "/me/messages";
            log.info("[API] POST /test/messenger/send-message psid={} text={}", psid, text);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("recipient", Map.of("id", psid));
            requestBody.put("message", Map.of("text", text));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(pageAccessToken);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            log.info("[API] POST /test/messenger/send-message success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending test message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Kiểm tra webhook subscription
     */
    @Operation(summary = "Kiểm tra webhook subscription", description = "Lấy danh sách subscriptions hiện tại của Page.")
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptions() {
        try {
            String url = messengerApiUrl + "/me/subscribed_apps?access_token=" + pageAccessToken;
            log.info("[API] GET /test/messenger/subscriptions");
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("[API] GET /test/messenger/subscriptions success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting subscriptions", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}




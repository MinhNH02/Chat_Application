package com.example.chat_demo.omnichannel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * MessengerUserProfileService - Lấy thông tin user profile từ Facebook Graph API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessengerUserProfileService {
    
    @Value("${platform.messenger.api-url:https://graph.facebook.com/v18.0}")
    private String messengerApiUrl;
    
    @Value("${platform.messenger.page-access-token}")
    private String pageAccessToken;
    
    private final RestTemplate restTemplate;
    
    /**
     * Lấy thông tin user profile từ PSID
     * @param psid Page-Scoped ID của user
     * @return Map chứa firstName, lastName, hoặc null nếu lỗi
     */
    public UserProfile getUserProfile(String psid) {
        try {
            String url = messengerApiUrl + "/" + psid + 
                    "?fields=first_name,last_name&access_token=" + pageAccessToken;
            
            log.debug("Fetching user profile for PSID: {}", psid);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response == null) {
                log.warn("No profile data returned for PSID: {}", psid);
                return null;
            }
            
            String firstName = (String) response.get("first_name");
            String lastName = (String) response.get("last_name");
            
            log.debug("Fetched profile for PSID {}: firstName={}, lastName={}", 
                    psid, firstName, lastName);
            
            return new UserProfile(firstName, lastName);
            
        } catch (Exception e) {
            log.warn("Failed to fetch user profile for PSID: {} - {}", psid, e.getMessage());
            // Không throw exception, chỉ log warning để không block message processing
            return null;
        }
    }
    
    /**
     * DTO cho user profile
     */
    public record UserProfile(String firstName, String lastName) {
    }
}




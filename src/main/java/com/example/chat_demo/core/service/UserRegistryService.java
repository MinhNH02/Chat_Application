package com.example.chat_demo.core.service;

import com.example.chat_demo.core.model.User;
import com.example.chat_demo.core.repository.UserRepository;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserRegistryService - Lưu user mới vào DB khi nhận tin nhắn đầu tiên
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistryService {
    
    private final UserRepository userRepository;
    
    /**
     * Đăng ký user mới hoặc trả về user đã tồn tại
     * @param unifiedMessage Message đã được chuẩn hóa
     * @return User entity và boolean isNewUser
     */
    @Transactional
    public User registerOrGetUser(UnifiedMessage unifiedMessage) {
        // Kiểm tra user đã tồn tại chưa
        log.debug("Registering or fetching user {} on {}", unifiedMessage.getPlatformUserId(), unifiedMessage.getChannelType());
        return userRepository
            .findByPlatformUserIdAndChannelType(
                unifiedMessage.getPlatformUserId(),
                unifiedMessage.getChannelType()
            )
            .orElseGet(() -> {
                // Tạo user mới
                User newUser = new User();
                newUser.setPlatformUserId(unifiedMessage.getPlatformUserId());
                newUser.setChannelType(unifiedMessage.getChannelType());
                newUser.setUsername(unifiedMessage.getUsername());
                newUser.setFirstName(unifiedMessage.getFirstName());
                newUser.setLastName(unifiedMessage.getLastName());
                newUser.setPhoneNumber(unifiedMessage.getPhoneNumber());
                
                User savedUser = userRepository.save(newUser);
                log.info("Registered new user: {} on platform {}", 
                    savedUser.getPlatformUserId(), 
                    savedUser.getChannelType());
                
                return savedUser;
            });
    }
    
    /**
     * Kiểm tra user có phải là user mới không (check trước khi register)
     * Dùng để quyết định có gửi welcome message không
     */
    public boolean isNewUser(UnifiedMessage unifiedMessage) {
        boolean isNew = !userRepository.existsByPlatformUserIdAndChannelType(
            unifiedMessage.getPlatformUserId(),
            unifiedMessage.getChannelType()
        );
        log.debug("Checked user {} on {} isNew={}", unifiedMessage.getPlatformUserId(), unifiedMessage.getChannelType(), isNew);
        return isNew;
    }
}


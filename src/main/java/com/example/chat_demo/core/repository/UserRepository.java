package com.example.chat_demo.core.repository;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPlatformUserIdAndChannelType(String platformUserId, ChannelType channelType);
    boolean existsByPlatformUserIdAndChannelType(String platformUserId, ChannelType channelType);
}


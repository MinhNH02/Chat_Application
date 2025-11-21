package com.example.chat_demo.core.repository;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserAndStatus(User user, String status);
    List<Conversation> findByUser(User user, Sort sort);
    List<Conversation> findByStatus(String status, Sort sort);
}


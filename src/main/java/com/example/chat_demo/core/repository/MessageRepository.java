package com.example.chat_demo.core.repository;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversation(Conversation conversation, Pageable pageable);
    Page<Message> findByConversationOrderByReceivedAtDesc(Conversation conversation, Pageable pageable);
}


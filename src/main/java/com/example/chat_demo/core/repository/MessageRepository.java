package com.example.chat_demo.core.repository;

import com.example.chat_demo.core.model.Conversation;
import com.example.chat_demo.core.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // Đếm tổng số tin nhắn trong conversation
    long countByConversation(Conversation conversation);
    
    // Load tin nhắn mới nhất (lần đầu mở chat)
    @Query("SELECT m FROM Message m WHERE m.conversation = :conversation ORDER BY m.id DESC")
    List<Message> findLatestMessages(@Param("conversation") Conversation conversation, Pageable pageable);
    
    // Load tin nhắn cũ hơn message_id (scroll lên)
    @Query("SELECT m FROM Message m WHERE m.conversation = :conversation AND m.id < :beforeMessageId ORDER BY m.id DESC")
    List<Message> findOlderMessages(@Param("conversation") Conversation conversation, 
                                    @Param("beforeMessageId") Long beforeMessageId, 
                                    Pageable pageable);
    
    // Tìm messages chưa đọc trong conversation
    List<Message> findByConversationAndStatusNot(Conversation conversation, Message.MessageStatus status);
    
    // Đếm số tin nhắn chưa đọc (inbound và chưa read)
    long countByConversationAndDirectionAndStatusNot(
        Conversation conversation, 
        Message.MessageDirection direction, 
        Message.MessageStatus status
    );
}


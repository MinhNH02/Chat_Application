package com.example.chat_demo.core.repository;

import com.example.chat_demo.core.model.Call;
import com.example.chat_demo.core.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {
    Optional<Call> findByJitsiRoomId(String jitsiRoomId);
    
    // Tìm room ID case-insensitive (để xử lý uppercase/lowercase)
    @Query("SELECT c FROM Call c WHERE LOWER(c.jitsiRoomId) = LOWER(:roomId)")
    Optional<Call> findByJitsiRoomIdIgnoreCase(@Param("roomId") String roomId);
    
    List<Call> findByConversationOrderByInitiatedAtDesc(Conversation conversation);
    
    List<Call> findByStatus(Call.CallStatus status);
    
    Optional<Call> findFirstByConversationAndStatusInOrderByInitiatedAtDesc(
        Conversation conversation, 
        List<Call.CallStatus> statuses
    );
}


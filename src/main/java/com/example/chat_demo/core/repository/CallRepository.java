package com.example.chat_demo.core.repository;

import com.example.chat_demo.core.model.Call;
import com.example.chat_demo.core.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {
    Optional<Call> findByJitsiRoomId(String jitsiRoomId);
    
    List<Call> findByConversationOrderByInitiatedAtDesc(Conversation conversation);
    
    List<Call> findByStatus(Call.CallStatus status);
    
    Optional<Call> findFirstByConversationAndStatusInOrderByInitiatedAtDesc(
        Conversation conversation, 
        List<Call.CallStatus> statuses
    );
}

